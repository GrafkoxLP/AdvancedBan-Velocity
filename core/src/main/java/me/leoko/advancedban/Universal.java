package me.leoko.advancedban;

import com.google.gson.Gson;
import me.leoko.advancedban.manager.*;
import me.leoko.advancedban.utils.Command;
import me.leoko.advancedban.utils.InterimData;
import me.leoko.advancedban.utils.Punishment;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This is the server independent entry point of the plugin.
 */
public class Universal {

    /**
     * True when the plugin was compiled with the {@code debug} Maven profile
     * ({@code mvn -Pdebug clean package}). When true, all {@link #debug(Object)}
     * calls bypass the config flag, and various subsystems emit verbose output.
     */
    public static final boolean IS_DEBUG_BUILD;
    public static final String BUILD_TAG;

    static {
        String tag = "RELEASE";
        try (InputStream in = Universal.class.getClassLoader().getResourceAsStream("build-info.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                tag = props.getProperty("build", "RELEASE");
            }
        } catch (IOException ignored) {
            // Fall back to RELEASE.
        }
        BUILD_TAG = tag;
        IS_DEBUG_BUILD = "DEBUG".equalsIgnoreCase(tag);
    }

    private static Universal instance = null;

    private final Map<String, String> ips = new ConcurrentHashMap<>();
    private MethodInterface mi;
    private LogManager logManager;

    private final Gson gson = new Gson();

    /**
     * Get universal.
     *
     * @return the universal instance
     */
    public static Universal get() {
        return instance == null ? instance = new Universal() : instance;
    }

    /**
     * Initially sets up the plugin.
     *
     * @param mi the mi
     */
    public void setup(MethodInterface mi) {
        this.mi = mi;
        mi.loadFiles();
        logManager = new LogManager();
        UpdateManager.get().setup();
        UUIDManager.get().setup();

        try {
            DatabaseManager.get().setup(mi.getBoolean(mi.getConfig(), "UseMySQL", false));
        } catch (Exception ex) {
            log("Failed enabling database-manager...");
            debugException(ex);
        }

        mi.setupMetrics();
        PunishmentManager.get().setup();
        DiscordManager.get().init();

        for (Command command : Command.values()) {
            for (String commandName : command.getNames()) {
                mi.setCommandExecutor(commandName, command.getPermission(), command.getTabCompleter());
            }
        }

        String upt = "You have the newest version";
        String response = getFromURL("https://api.spigotmc.org/legacy/update.php?resource=8695");
        if (response == null) {
            upt = "Failed to check for updates :(";
        } else {
            int cmp = compareVersions(mi.getVersion(), response);
            if (cmp < 0) {
                upt = "There is a new version available! [" + response + "]";
            } else if (cmp > 0) {
                upt = "Ahead of upstream (upstream: " + response + ")";
            }
            // cmp == 0 -> "You have the newest version"
        }

        if (IS_DEBUG_BUILD) {
            mi.log("§4§l!!! AdvancedBan DEBUG BUILD ACTIVE — verbose logging enabled !!!");
        }

        if (mi.getBoolean(mi.getConfig(), "DetailedEnableMessage", true)) {
            mi.log("\n \n&8[]=====[&7Enabling AdvancedBan&8]=====[]&r"
                    + "\n&8| &cInformation:&r"
                    + "\n&8|   &cName: &7AdvancedBan&r"
                    + "\n&8|   &cOriginal author: &7Leoko&r"
                    + "\n&8|   &cMaintainer: &7Leon (Grafkox_LP)&r"
                    + "\n&8|   &cVersion: &7" + mi.getVersion() + " (" + BUILD_TAG + ")&r"
                    + "\n&8|   &cStorage: &7" + (DatabaseManager.get().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)") + "&r"
                    + "\n&8| &cUpdate:&r"
                    + "\n&8|   &7" + upt + "&r"
                    + "\n&8[]================================[]&r\n ");
        } else {
            mi.log("&cEnabling AdvancedBan on Version &7" + mi.getVersion() + " (" + BUILD_TAG + ")&r");
            mi.log("&cOriginal by Leoko &8• &cMaintained by Leon (Grafkox_LP)&r");
        }
    }

    /**
     * Shutdown.
     */
    public void shutdown() {
        DatabaseManager.get().shutdown();

        if (mi.getBoolean(mi.getConfig(), "DetailedDisableMessage", true)) {
            mi.log("\n \n&8[]=====[&7Disabling AdvancedBan&8]=====[]"
                    + "\n&8| &cInformation:"
                    + "\n&8|   &cName: &7AdvancedBan"
                    + "\n&8|   &cOriginal author: &7Leoko"
                    + "\n&8|   &cMaintainer: &7Leon (Grafkox_LP)"
                    + "\n&8|   &cVersion: &7" + getMethods().getVersion()
                    + "\n&8|   &cStorage: &7" + (DatabaseManager.get().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)")
                    + "\n&8[]================================[]&r\n ");
        } else {
            mi.log("&cDisabling AdvancedBan on Version &7" + getMethods().getVersion());
            mi.log("&cOriginal by Leoko &8• &cMaintained by Leon (Grafkox_LP)");
        }
    }

    /**
     * Gets methods.
     *
     * @return the methods
     */
    public MethodInterface getMethods() {
        return mi;
    }

    /**
     * Is bungee boolean.
     *
     * @return the boolean
     */
    public boolean isBungee() {
        return mi.isBungee();
    }

    public Map<String, String> getIps() {
        return ips;
    }

    public Gson getGson() {
        return gson;
    }

    /**
     * Compare two dotted version strings (e.g. "2.4.0" vs "2.3.0").
     * Returns negative if {@code a < b}, positive if {@code a > b}, zero if equal.
     * Non-numeric suffixes (e.g. "-RELEASE") are ignored.
     */
    static int compareVersions(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int va = i < partsA.length ? parseLeadingInt(partsA[i]) : 0;
            int vb = i < partsB.length ? parseLeadingInt(partsB[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseLeadingInt(String s) {
        if (s == null) return 0;
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Gets from url.
     *
     * @param surl the surl
     * @return the from url
     */
    public String getFromURL(String surl) {
        String response = null;
        try (Scanner s = new Scanner(URI.create(surl).toURL().openStream())) {
            if (s.hasNext()) {
                response = s.next();
            }
        } catch (IOException | IllegalArgumentException exc) {
            debug("!! Failed to connect to URL: " + surl);
        }
        return response;
    }

    /**
     * Is mute command boolean.
     *
     * @param cmd the cmd
     * @return the boolean
     */
    public boolean isMuteCommand(String cmd) {
        return isMuteCommand(cmd, getMethods().getStringList(getMethods().getConfig(), "MuteCommands"));
    }

    /**
     * Visible for testing. Do not use this. Please use {@link #isMuteCommand(String)}.
     *
     * @param cmd          the command
     * @param muteCommands the mute commands from the config
     * @return true if the command matched any of the mute commands.
     */
    boolean isMuteCommand(String cmd, List<String> muteCommands) {
        String[] words = cmd.split(" ");
        // Handle commands with colons
        if (words[0].indexOf(':') != -1) {
            words[0] = words[0].split(":", 2)[1];
        }
        for (String muteCommand : muteCommands) {
            if (muteCommandMatches(words, muteCommand)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Visible for testing. Do not use this.
     *
     * @param commandWords the command run by a player, separated into its words
     * @param muteCommand a mute command from the config
     * @return true if they match, false otherwise
     */
    boolean muteCommandMatches(String[] commandWords, String muteCommand) {
        // Basic equality check
        if (commandWords[0].equalsIgnoreCase(muteCommand)) {
            return true;
        }
        // Advanced equality check
        // Essentially a case-insensitive "startsWith" for arrays
        if (muteCommand.indexOf(' ') != -1) {
            String[] muteCommandWords = muteCommand.split(" ");
            if (muteCommandWords.length > commandWords.length) {
                return false;
            }
            for (int n = 0; n < muteCommandWords.length; n++) {
                if (!muteCommandWords[n].equalsIgnoreCase(commandWords[n])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Is exempt player boolean.
     *
     * @param name the name
     * @return the boolean
     */
    public boolean isExemptPlayer(String name) {
        List<String> exempt = getMethods().getStringList(getMethods().getConfig(), "ExemptPlayers");
        if (exempt != null) {
            for (String str : exempt) {
                if (name.equalsIgnoreCase(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Call connection string.
     *
     * @param name the name
     * @param ip   the ip
     * @return the string
     */
    public String callConnection(String name, String ip) {
        name = name.toLowerCase();
        String uuid = UUIDManager.get().getUUID(name);
        if (uuid == null) return "[AdvancedBan] Failed to fetch your UUID";

        if (ip != null) {
            getIps().remove(name);
            getIps().put(name, ip);
        }

        InterimData interimData = PunishmentManager.get().load(name, uuid, ip);

        if (interimData == null) {
            if (getMethods().getBoolean(mi.getConfig(), "LockdownOnError", true)) {
                return "[AdvancedBan] Failed to load player data!";
            } else {
                return null;
            }
        }

        Punishment pt = interimData.getBan();

        if (pt == null) {
            interimData.accept();
            return null;
        }

        return pt.getLayoutBSN();
    }

    /**
     * Has perms boolean.
     *
     * @param player the player
     * @param perms  the perms
     * @return the boolean
     */
    public boolean hasPerms(Object player, String perms) {
        if (mi.hasPerms(player, perms)) {
            return true;
        }

        if (mi.getBoolean(mi.getConfig(), "EnableAllPermissionNodes", false)) {
            while (perms.contains(".")) {
                perms = perms.substring(0, perms.lastIndexOf('.'));
                if (mi.hasPerms(player, perms + ".all")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Log.
     *
     * @param msg the msg
     */
    public void log(String msg) {
        mi.log("§8[§cAdvancedBan§8] §7" + msg);
        debugToFile(msg);
    }

    /**
     * Debug.
     *
     * @param msg the msg
     */
    public void debug(Object msg) {
        if (IS_DEBUG_BUILD || mi.getBoolean(mi.getConfig(), "Debug", false)) {
            mi.log("§8[§cAdvancedBan§8] §cDebug: §7" + msg.toString());
        }
        debugToFile(msg);
    }

    public void debugException(Exception exc) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exc.printStackTrace(pw);
        if (IS_DEBUG_BUILD) {
            mi.log("§8[§cAdvancedBan§8] §cDebug: §7" + sw);
        }
        debug(sw.toString());
    }

    /**
     * Debug.
     *
     * @param ex the ex
     */
    public void debugSqlException(SQLException ex) {
        if (IS_DEBUG_BUILD || mi.getBoolean(mi.getConfig(), "Debug", false)) {
            debug("§7An error has occurred with the database, the error code is: '" + ex.getErrorCode() + "'");
            debug("§7The state of the sql is: " + ex.getSQLState());
            debug("§7Error message: " + ex.getMessage());
        }
        debugException(ex);
    }

    private void debugToFile(Object msg) {
        File debugFile = new File(mi.getDataFolder(), "logs/latest.log");
        if (!debugFile.exists()) {
            try {
                debugFile.createNewFile();
            } catch (IOException ex) {
                System.out.print("An error has occurred creating the 'latest.log' file again, check your server.");
                System.out.print("Error message" + ex.getMessage());
            }
        } else {
            logManager.checkLastLog(false);
        }
        try {
            FileUtils.writeStringToFile(debugFile, "[" + new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()) + "] " + mi.clearFormatting(msg.toString()) + "\n", "UTF8", true);
        } catch (IOException ex) {
            System.out.print("An error has occurred writing to 'latest.log' file.");
            System.out.print(ex.getMessage());
        }
    }
}
