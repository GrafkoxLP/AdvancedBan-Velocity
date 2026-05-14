package me.leoko.advancedban.velocity;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.utils.Permissionable;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.tabcompletion.TabCompleter;
import me.leoko.advancedban.velocity.command.CommandReceiverVelocity;
import me.leoko.advancedban.velocity.event.PunishmentEvent;
import me.leoko.advancedban.velocity.event.RevokePunishmentEvent;
import me.leoko.advancedban.velocity.permissions.LuckPermsOfflineUserVelocity;
import me.leoko.advancedban.velocity.util.YamlConfig;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bstats.charts.SimplePie;
import org.bstats.velocity.Metrics;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Velocity implementation of {@link MethodInterface}. Mirrors the BungeeCord behavior in
 * {@code BungeeMethods} as closely as the Velocity API allows.
 *
 * Key differences from Bungee:
 *   - Configuration is loaded via SnakeYAML directly (Velocity ships it transitively).
 *   - Scheduling uses Velocity's {@code Scheduler.buildTask}.
 *   - Components use kyori-adventure for legacy color translation.
 *   - {@link #isBungee()} returns {@code true} — the flag means "is proxy" in core code.
 *     Renaming is out of scope for 2.4.0.
 */
public class VelocityMethods implements MethodInterface {

    // Register your own bStats plugin and update this ID, or leave 0 to disable metrics.
    private static final int BSTATS_PLUGIN_ID = 0;

    private final VelocityMain plugin;
    private final ProxyServer server;
    private final Path dataDirectory;

    private final File configFile;
    private final File messageFile;
    private final File layoutFile;
    private final File mysqlFile;

    private YamlConfig config;
    private YamlConfig messages;
    private YamlConfig layouts;
    private YamlConfig mysql;

    private final Function<String, Permissionable> permissionableGenerator;

    public VelocityMethods(VelocityMain plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.dataDirectory = plugin.getDataDirectory();
        this.configFile = dataDirectory.resolve("config.yml").toFile();
        this.messageFile = dataDirectory.resolve("Messages.yml").toFile();
        this.layoutFile = dataDirectory.resolve("Layouts.yml").toFile();
        this.mysqlFile = dataDirectory.resolve("MySQL.yml").toFile();

        if (server.getPluginManager().getPlugin("luckperms").isPresent()) {
            this.permissionableGenerator = LuckPermsOfflineUserVelocity::new;
            log("[AdvancedBan] Offline permission support through LuckPerms active");
        } else {
            this.permissionableGenerator = null;
            log("[AdvancedBan] No offline permission support (LuckPerms not detected)");
        }
    }

    @Override
    public void loadFiles() {
        try {
            Files.createDirectories(dataDirectory);
            copyResource("config.yml", configFile);
            copyResource("Messages.yml", messageFile);
            copyResource("Layouts.yml", layoutFile);

            config = YamlConfig.load(configFile.toPath());
            messages = YamlConfig.load(messageFile.toPath());
            layouts = YamlConfig.load(layoutFile.toPath());

            mysql = mysqlFile.exists()
                    ? YamlConfig.load(mysqlFile.toPath())
                    : YamlConfig.load(configFile.toPath());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void copyResource(String name, File target) throws IOException {
        if (target.exists()) return;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Bundled resource missing: " + name);
            }
            Files.copy(in, target.toPath());
        }
    }

    @Override
    public String getFromUrlJson(String url, String key) {
        try {
            HttpURLConnection request = (HttpURLConnection) URI.create(url).toURL().openConnection();
            request.connect();

            JsonObject json = (JsonObject) JsonParser.parseReader(new InputStreamReader(request.getInputStream()));

            String[] keys = key.split("\\|");
            for (int i = 0; i < keys.length - 1; i++) {
                json = json.getAsJsonObject(keys[i]);
            }

            return json.get(keys[keys.length - 1]).toString().replaceAll("\"", "");
        } catch (Exception exc) {
            return null;
        }
    }

    @Override
    public String getVersion() {
        return plugin.getContainer().getDescription().getVersion().orElse("dev");
    }

    @Override
    public String[] getKeys(Object file, String path) {
        return ((YamlConfig) file).getKeys(path);
    }

    @Override
    public YamlConfig getConfig() {
        return config;
    }

    @Override
    public YamlConfig getMessages() {
        return messages;
    }

    @Override
    public YamlConfig getLayouts() {
        return layouts;
    }

    @Override
    public void setupMetrics() {
        if (BSTATS_PLUGIN_ID <= 0) return;
        Metrics metrics = plugin.getMetricsFactory().make(plugin, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("MySQL", () -> DatabaseManager.get().isUseMySQL() ? "yes" : "no"));
    }

    @Override
    public boolean isBungee() {
        // Misnomer: this flag is read as "am I a proxy?" by the core. Velocity is a proxy.
        return true;
    }

    @Override
    public String clearFormatting(String text) {
        if (text == null) return null;
        Component component = LegacyComponentSerializer.legacySection().deserialize(text);
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Override
    public Object getPlugin() {
        return plugin;
    }

    @Override
    public File getDataFolder() {
        return dataDirectory.toFile();
    }

    @Override
    public void setCommandExecutor(String cmd, String permission, TabCompleter tabCompleter) {
        CommandManager cm = server.getCommandManager();
        CommandMeta meta = cm.metaBuilder(cmd).plugin(plugin).build();
        cm.register(meta, new CommandReceiverVelocity(server, cmd, permission));
    }

    @Override
    public void sendMessage(Object player, String msg) {
        if (player == null || msg == null) return;
        Component component = LegacyComponentSerializer.legacySection().deserialize(msg.replace('&', '§'));
        if (player instanceof com.velocitypowered.api.command.CommandSource) {
            ((com.velocitypowered.api.command.CommandSource) player).sendMessage(component);
        }
    }

    @Override
    public boolean hasPerms(Object player, String perms) {
        return player != null
                && player instanceof com.velocitypowered.api.command.CommandSource
                && ((com.velocitypowered.api.command.CommandSource) player).hasPermission(perms);
    }

    @Override
    public Permissionable getOfflinePermissionPlayer(String name) {
        if (permissionableGenerator != null) {
            return permissionableGenerator.apply(name);
        }
        return permission -> false;
    }

    @Override
    public boolean isOnline(String name) {
        return server.getPlayer(name).isPresent();
    }

    @Override
    public Object getPlayer(String name) {
        return server.getPlayer(name).orElse(null);
    }

    @Override
    public void kickPlayer(String player, String reason) {
        server.getPlayer(player).ifPresent(p -> p.disconnect(
                LegacyComponentSerializer.legacySection().deserialize(reason == null ? "" : reason)));
    }

    @Override
    public Object[] getOnlinePlayers() {
        return server.getAllPlayers().toArray(new Player[0]);
    }

    @Override
    public void scheduleAsyncRep(Runnable rn, long l1, long l2) {
        server.getScheduler()
                .buildTask(plugin, rn)
                .delay(l1 * 50, TimeUnit.MILLISECONDS)
                .repeat(l2 * 50, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Override
    public void scheduleAsync(Runnable rn, long l1) {
        server.getScheduler()
                .buildTask(plugin, rn)
                .delay(l1 * 50, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Override
    public void runAsync(Runnable rn) {
        server.getScheduler().buildTask(plugin, rn).schedule();
    }

    @Override
    public void runSync(Runnable rn) {
        // Velocity has no main thread, run directly. Same compromise as Bungee.
        rn.run();
    }

    @Override
    public void executeCommand(String cmd) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), cmd);
    }

    @Override
    public String getName(Object player) {
        if (player instanceof Player) return ((Player) player).getUsername();
        if (player instanceof com.velocitypowered.api.command.CommandSource) {
            return ((com.velocitypowered.api.command.CommandSource) player).get(Identity.NAME).orElse("CONSOLE");
        }
        return "CONSOLE";
    }

    @Override
    public String getName(String uuid) {
        return server.getPlayer(UUID.fromString(uuid)).map(Player::getUsername).orElse(null);
    }

    @Override
    public String getIP(Object player) {
        return ((Player) player).getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public String getInternUUID(Object player) {
        return player instanceof Player
                ? ((Player) player).getUniqueId().toString().replace("-", "")
                : "none";
    }

    @Override
    public String getInternUUID(String player) {
        return server.getPlayer(player)
                .map(p -> p.getUniqueId().toString().replace("-", ""))
                .orElse(null);
    }

    @Override
    public boolean callChat(Object player) {
        Punishment pnt = PunishmentManager.get().getMute(UUIDManager.get().getUUID(getName(player)));
        if (pnt != null) {
            for (String str : pnt.getLayout()) {
                sendMessage(player, str);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean callCMD(Object player, String cmd) {
        Punishment pnt;
        if (Universal.get().isMuteCommand(cmd.substring(1))
                && (pnt = PunishmentManager.get().getMute(UUIDManager.get().getUUID(getName(player)))) != null) {
            for (String str : pnt.getLayout()) {
                sendMessage(player, str);
            }
            return true;
        }
        return false;
    }

    @Override
    public Object getMySQLFile() {
        return mysql;
    }

    @Override
    public String parseJSON(InputStreamReader json, String key) {
        JsonElement element = JsonParser.parseReader(json);
        if (element instanceof JsonNull) return null;
        JsonElement obj = ((JsonObject) element).get(key);
        return obj != null ? obj.toString().replaceAll("\"", "") : null;
    }

    @Override
    public String parseJSON(String json, String key) {
        JsonElement element = JsonParser.parseString(json);
        if (element instanceof JsonNull) return null;
        JsonElement obj = ((JsonObject) element).get(key);
        return obj != null ? obj.toString().replaceAll("\"", "") : null;
    }

    @Override
    public Boolean getBoolean(Object file, String path) {
        return ((YamlConfig) file).getBoolean(path);
    }

    @Override
    public String getString(Object file, String path) {
        return ((YamlConfig) file).getString(path);
    }

    @Override
    public Long getLong(Object file, String path) {
        return ((YamlConfig) file).getLong(path);
    }

    @Override
    public Integer getInteger(Object file, String path) {
        return ((YamlConfig) file).getInt(path);
    }

    @Override
    public List<String> getStringList(Object file, String path) {
        return ((YamlConfig) file).getStringList(path);
    }

    @Override
    public boolean getBoolean(Object file, String path, boolean def) {
        return ((YamlConfig) file).getBoolean(path, def);
    }

    @Override
    public String getString(Object file, String path, String def) {
        return ((YamlConfig) file).getString(path, def);
    }

    @Override
    public long getLong(Object file, String path, long def) {
        return ((YamlConfig) file).getLong(path, def);
    }

    @Override
    public int getInteger(Object file, String path, int def) {
        return ((YamlConfig) file).getInt(path, def);
    }

    @Override
    public boolean contains(Object file, String path) {
        return ((YamlConfig) file).contains(path);
    }

    @Override
    public String getFileName(Object file) {
        return "[Only available on Bukkit-Version!]";
    }

    @Override
    public void callPunishmentEvent(Punishment punishment) {
        server.getEventManager().fireAndForget(new PunishmentEvent(punishment));
    }

    @Override
    public void callRevokePunishmentEvent(Punishment punishment, boolean massClear) {
        server.getEventManager().fireAndForget(new RevokePunishmentEvent(punishment, massClear));
    }

    @Override
    public boolean isOnlineMode() {
        return server.getConfiguration().isOnlineMode();
    }

    @Override
    public void notify(String perm, List<String> notification) {
        server.getAllPlayers()
                .stream()
                .filter(p -> Universal.get().hasPerms(p, perm))
                .forEach(p -> notification.forEach(line -> sendMessage(p, line)));
    }

    @Override
    public void log(String msg) {
        if (msg == null) return;
        Component component = LegacyComponentSerializer.legacySection().deserialize(msg.replace('&', '§'));
        server.getConsoleCommandSource().sendMessage(component);
    }

    @Override
    public boolean isUnitTesting() {
        return false;
    }

    @Override
    public Object loadYamlFile(File file) {
        try {
            return YamlConfig.load(file.toPath());
        } catch (IOException exc) {
            Universal.get().debugException(exc);
            return null;
        }
    }
}
