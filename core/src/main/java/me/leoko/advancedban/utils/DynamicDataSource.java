package me.leoko.advancedban.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;

public class DynamicDataSource {
    private final HikariConfig config = new HikariConfig();

    public DynamicDataSource(boolean preferMySQL) throws ClassNotFoundException {
        MethodInterface mi = Universal.get().getMethods();
        if (preferMySQL) {
            String ip = mi.getString(mi.getMySQLFile(), "MySQL.IP", "Unknown");
            String dbName = mi.getString(mi.getMySQLFile(), "MySQL.DB-Name", "Unknown");
            String usrName = mi.getString(mi.getMySQLFile(), "MySQL.Username", "Unknown");
            String password = mi.getString(mi.getMySQLFile(), "MySQL.Password", "Unknown");
            String properties = mi.getString(mi.getMySQLFile(), "MySQL.Properties",
                    "verifyServerCertificate=false&useSSL=false&useUnicode=true&characterEncoding=utf8");
            int port = mi.getInteger(mi.getMySQLFile(), "MySQL.Port", 3306);

            Class.forName("com.mysql.jdbc.Driver");
            config.setJdbcUrl("jdbc:mysql://" + ip + ":" + port + "/" + dbName + "?" + properties);
            config.setUsername(usrName);
            config.setPassword(password);

            // Sensible tuning for proxy-side punishment lookups.
            // Override in MySQL.yml under "MySQL.Pool.*" if needed.
            config.setMaximumPoolSize(mi.getInteger(mi.getMySQLFile(), "MySQL.Pool.MaximumPoolSize",
                    Math.max(4, Runtime.getRuntime().availableProcessors() * 2)));
            config.setMinimumIdle(mi.getInteger(mi.getMySQLFile(), "MySQL.Pool.MinimumIdle", 2));
            config.setConnectionTimeout(mi.getLong(mi.getMySQLFile(), "MySQL.Pool.ConnectionTimeout", 10_000L));
            config.setIdleTimeout(mi.getLong(mi.getMySQLFile(), "MySQL.Pool.IdleTimeout", 600_000L));
            config.setMaxLifetime(mi.getLong(mi.getMySQLFile(), "MySQL.Pool.MaxLifetime", 1_800_000L));

            // Prepared-statement cache — big throughput win on punishment lookups.
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize",
                    String.valueOf(mi.getInteger(mi.getMySQLFile(), "MySQL.Pool.PrepStmtCacheSize", 250)));
            config.addDataSourceProperty("prepStmtCacheSqlLimit",
                    String.valueOf(mi.getInteger(mi.getMySQLFile(), "MySQL.Pool.PrepStmtCacheSqlLimit", 2048)));
            config.addDataSourceProperty("useServerPrepStmts", "true");
        } else {
            // No need to worry about relocation because the maven-shade-plugin also changes strings
            String driverClassName = "org.hsqldb.jdbc.JDBCDriver";
            Class.forName(driverClassName);
            config.setDriverClassName(driverClassName);
            config.setJdbcUrl("jdbc:hsqldb:file:" + mi.getDataFolder().getPath() + "/data/storage;hsqldb.lock_file=false");
            config.setUsername("SA");
            config.setPassword("");

            // HSQLDB is local — small pool is enough.
            config.setMaximumPoolSize(4);
            config.setMinimumIdle(1);
        }

        // Friendly pool name in logs.
        config.setPoolName("AdvancedBan-Pool");
    }

    public HikariDataSource generateDataSource() {
        return new HikariDataSource(config);
    }
}
