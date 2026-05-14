package me.leoko.advancedban.manager;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import me.leoko.advancedban.utils.discord.DiscordPayload;
import me.leoko.advancedban.utils.discord.EmbedTemplate;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Discord webhook notifier.
 *
 * Drop-in replacement for the {@code Discord Ban Announcer} plugin's AdvancedBan integration.
 * Reads its configuration from {@code plugins/AdvancedBan/discord/config.yml} (DBA-compatible
 * format) and embed templates from {@code plugins/AdvancedBan/discord/embed/*.json}.
 *
 * The manager hooks the punishment lifecycle in {@link Punishment#create(boolean)} and
 * {@link Punishment#delete(String, boolean, boolean)}, so notifications fire for every
 * punishment regardless of source (command, plugin-message relay, auto-expiry).
 */
public final class DiscordManager {

    private static final String[] EMBED_FILES = {
            "ban", "tempban", "banip", "tempbanip",
            "kick", "mute", "tempmute",
            "warn", "tempwarn", "note",
            "unban", "unbanip", "unmute", "unwarn", "unnote"
    };

    private static DiscordManager instance;

    public static synchronized DiscordManager get() {
        if (instance == null) instance = new DiscordManager();
        return instance;
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private boolean enabled = false;
    private String webhookUrl = "";
    private String username = "AdvancedBan";
    private String avatarUrl = "";
    private String consoleName = "Console";
    private String automaticSuffix = "Strafe abgelaufen";
    private final Map<String, String> messageMapping = new HashMap<>();
    private final Map<String, EmbedTemplate> embedTemplates = new HashMap<>();

    private DiscordManager() {}

    /**
     * Initialise the manager. Called from {@link Universal#setup}. Copies default config
     * and embed files to the data folder if missing, then loads them into memory.
     * Failures are logged and disable the manager — they never propagate.
     */
    public void init() {
        try {
            MethodInterface mi = Universal.get().getMethods();
            File discordDir = new File(mi.getDataFolder(), "discord");
            File embedDir = new File(discordDir, "embed");
            File configFile = new File(discordDir, "config.yml");

            if (!discordDir.exists() && !discordDir.mkdirs()) {
                Universal.get().log("§cFailed to create Discord folder, Discord notifications disabled.");
                return;
            }
            if (!embedDir.exists() && !embedDir.mkdirs()) {
                Universal.get().log("§cFailed to create Discord embed folder, Discord notifications disabled.");
                return;
            }

            if (!configFile.exists()) {
                copyResource("discord/config.yml", configFile);
            }
            for (String name : EMBED_FILES) {
                File target = new File(embedDir, name + ".json");
                if (!target.exists()) {
                    copyResource("discord/embed/" + name + ".json", target);
                }
            }

            Object discordConfig = mi.loadYamlFile(configFile);
            if (discordConfig == null) {
                Universal.get().log("§cFailed to load discord/config.yml — Discord notifications disabled.");
                return;
            }

            this.webhookUrl = nullSafe(mi.getString(discordConfig, "webhook-url", ""));
            this.username = nullSafe(mi.getString(discordConfig, "username", "AdvancedBan"));
            this.avatarUrl = nullSafe(mi.getString(discordConfig, "avatar-url", ""));
            this.consoleName = nullSafe(mi.getString(discordConfig, "console-name", "Console"));
            this.automaticSuffix = nullSafe(mi.getString(discordConfig, "automatic", "Strafe abgelaufen"));

            messageMapping.clear();
            // Pull every key under "messages" — keep this resilient to extra/missing keys.
            String[] msgKeys;
            try {
                msgKeys = mi.getKeys(discordConfig, "messages");
            } catch (Exception ex) {
                msgKeys = new String[0];
            }
            for (String key : msgKeys) {
                String value = mi.getString(discordConfig, "messages." + key);
                if (value != null) messageMapping.put(key.toLowerCase(), value);
            }

            embedTemplates.clear();
            for (String name : EMBED_FILES) {
                File embedFile = new File(embedDir, name + ".json");
                if (!embedFile.exists()) continue;
                try {
                    String json = new String(Files.readAllBytes(embedFile.toPath()), StandardCharsets.UTF_8);
                    embedTemplates.put(name, new EmbedTemplate(json));
                } catch (Exception ex) {
                    Universal.get().log("§cFailed to load Discord embed " + name + ".json: " + ex.getMessage());
                    Universal.get().debugException(ex);
                }
            }

            this.enabled = !webhookUrl.isEmpty();
            if (this.enabled) {
                Universal.get().log("§aDiscord webhook notifications enabled.");
            } else {
                Universal.get().log("§7Discord webhook notifications inactive (no webhook-url set).");
            }
        } catch (Exception ex) {
            this.enabled = false;
            Universal.get().log("§cDiscordManager init failed — notifications disabled.");
            Universal.get().debugException(ex);
        }
    }

    /**
     * Fired right after {@code Punishment.create()}. Looks up the message template
     * for the punishment type and dispatches asynchronously.
     */
    public void onPunish(Punishment punishment) {
        if (!enabled) return;
        String key = punishKey(punishment.getType());
        if (key == null) return;
        sendForPunishment(key, punishment, false);
    }

    /**
     * Fired right after {@code Punishment.delete()}. The {@code automatic} flag indicates
     * the revoke originated from the auto-expiry sweep in {@link PunishmentManager}.
     */
    public void onRevoke(Punishment punishment, boolean automatic) {
        if (!enabled) return;
        String key = revokeKey(punishment.getType());
        if (key == null) return;
        sendForPunishment(key, punishment, automatic);
    }

    private void sendForPunishment(String key, Punishment punishment, boolean automatic) {
        String mapping = messageMapping.get(key.toLowerCase());
        if (mapping == null || mapping.isEmpty()) return;

        Map<String, String> placeholders = buildPlaceholders(punishment, automatic);

        DiscordPayload payload = new DiscordPayload()
                .username(username)
                .avatarUrl(avatarUrl);

        if (mapping.startsWith("{embed:") && mapping.endsWith("}")) {
            String embedName = mapping.substring("{embed:".length(), mapping.length() - 1).trim();
            EmbedTemplate template = embedTemplates.get(embedName.toLowerCase());
            if (template == null) {
                Universal.get().debug("Discord embed template not found: " + embedName);
                return;
            }
            payload.addEmbed(template.apply(placeholders));
        } else {
            // Plain text content — replace placeholders inline.
            String content = mapping;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                content = content.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
            }
            payload.content(content);
        }

        String body = payload.toJson();
        Universal.get().getMethods().runAsync(() -> dispatch(body));
    }

    private void dispatch(String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "AdvancedBan-Discord/2.4.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 != 2) {
                Universal.get().debug("Discord webhook returned status " + status + ": " + response.body());
            } else if (Universal.IS_DEBUG_BUILD) {
                Universal.get().debug("Discord webhook OK (" + status + ")");
            }
        } catch (Exception ex) {
            Universal.get().debug("Discord webhook POST failed: " + ex.getMessage());
            Universal.get().debugException(ex);
        }
    }

    private Map<String, String> buildPlaceholders(Punishment p, boolean automatic) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("player", nullSafe(p.getName()));
        String operator = p.getOperator();
        if (operator == null || operator.isEmpty() || operator.equalsIgnoreCase("CONSOLE")) {
            operator = consoleName;
        }
        if (automatic && !automaticSuffix.isEmpty()) {
            operator = operator + " (" + automaticSuffix + ")";
        }
        map.put("staff", operator);
        map.put("reason", nullSafe(p.getReason()));
        map.put("duration", p.getType().isTemp() ? p.getDuration(true) : "permanent");
        map.put("id", String.valueOf(p.getId()));
        map.put("uuid", nullSafe(p.getUuid()));
        map.put("type", p.getType().getName());
        return map;
    }

    private static String punishKey(PunishmentType type) {
        switch (type) {
            case BAN: return "ban";
            case TEMP_BAN: return "tempban";
            case IP_BAN: return "banip";
            case TEMP_IP_BAN: return "tempbanip";
            case KICK: return "kick";
            case MUTE: return "mute";
            case TEMP_MUTE: return "tempmute";
            case WARNING: return "warn";
            case TEMP_WARNING: return "tempwarn";
            case NOTE: return "note";
            default: return null;
        }
    }

    private static String revokeKey(PunishmentType type) {
        switch (type) {
            case BAN:
            case TEMP_BAN: return "unban";
            case IP_BAN:
            case TEMP_IP_BAN: return "unbanip";
            case MUTE:
            case TEMP_MUTE: return "unmute";
            case WARNING:
            case TEMP_WARNING: return "unwarn";
            case NOTE: return "unnote";
            case KICK: // Kicks have no revoke event.
            default: return null;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private void copyResource(String resource, File target) throws IOException {
        try (InputStream in = DiscordManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                Universal.get().log("§cMissing bundled Discord resource: " + resource);
                return;
            }
            FileUtils.copyInputStreamToFile(in, target);
        }
    }
}
