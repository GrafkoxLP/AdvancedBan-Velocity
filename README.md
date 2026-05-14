# AdvancedBan

A maintained Velocity-ready fork of DevLeoko's [AdvancedBan](https://github.com/DevLeoko/AdvancedBan).
All-in-one punishment system for Bukkit/Spigot, BungeeCord and Velocity — with a built-in
Discord webhook notifier, modernized for Java 21.

![supports Bukkit • Bungee • Velocity](https://img.shields.io/badge/platforms-Bukkit%20%E2%80%A2%20Bungee%20%E2%80%A2%20Velocity-blue)
![Java 21](https://img.shields.io/badge/java-21-orange)
![license GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-lightgrey)

> **Original by Leoko** — maintained as a fork by **Leon (Grafkox_LP)**.
> The upstream project has been unmaintained for years; this fork brings it forward.

---

## What's in this fork (2.4.0)

- **Velocity 3.3.x / 3.4.x support** with full feature parity to the BungeeCord version
  - Login-time ban check via `EventTask.async` (proper Velocity intent pattern)
  - Mute enforcement on `PlayerChatEvent` + `CommandExecuteEvent`
  - Plugin-message bridge to Bukkit backend servers
  - Tab completion via `RawCommand`
  - LuckPerms offline-permission integration
- **Built-in Discord webhook notifier** — drop-in replacement for the Discord Ban
  Announcer plugin's AdvancedBan integration. Same file format
  (`discord/config.yml` + `discord/embed/*.json`), no Discord bot or token needed.
- **Java 21** baseline, modern dependencies (HikariCP 5.1, gson 2.11, hsqldb 2.7.4, …),
  single `mvn clean package` build.
- **Debug build profile** — `mvn -Pdebug clean package` produces `*-DEBUG.jar` variants
  with SQL/HTTP/event tracing for troubleshooting.
- **HikariCP pool tuning** — exposed via `MySQL.yml`, sensible defaults out of the box.
- **Cleanup** — abandoned CloudNet v2/v3 and RedisBungee integrations dropped.
  CloudNet v4 handles its own cross-server kicks via its bridge plugin.

---

## Features

- **Bans / Tempbans / IP bans / Tempipbans** — permanent or duration-based
- **Mutes / Tempmutes** — including configurable command blacklist (no `/me` bypass)
- **Warns / Tempwarns** — with configurable warn-action escalation
- **Kicks**, **Notes** — full history for moderators
- **Layouts** — pre-canned ban-reason templates with placeholders
- **MySQL or local HSQLDB** storage
- **Vault** (Bukkit) and **LuckPerms** (Bungee/Velocity) for offline permission checks
- **Discord notifications** for every punishment type, fully customizable per type

---

## Installation

Grab the right jar for your platform:

| Platform                | Jar                                            |
| ----------------------- | ---------------------------------------------- |
| Bukkit / Spigot / Paper | `AdvancedBan-Universal-2.4.0-RELEASE.jar`      |
| BungeeCord              | `AdvancedBan-Universal-2.4.0-RELEASE.jar`      |
| Velocity                | `AdvancedBan-Velocity-2.4.0-RELEASE.jar`       |

Drop into your server's `plugins/` directory and restart.

On first launch, AdvancedBan creates `plugins/AdvancedBan/`:

```
plugins/AdvancedBan/
├── config.yml              # main config (MySQL toggle, mute-commands, exempt list, …)
├── Messages.yml            # all user-facing messages
├── Layouts.yml             # reason templates (#examplelayout etc.)
├── MySQL.yml               # split out if you prefer; otherwise reads from config.yml
└── discord/
    ├── config.yml          # Discord webhook + per-type message mapping
    └── embed/              # one .json embed template per punishment type
        ├── ban.json
        ├── tempban.json
        ├── …
        └── unnote.json
```

---

## Discord setup

1. In your Discord channel, go to **Edit channel → Integrations → Webhooks → New Webhook**.
2. Copy the webhook URL.
3. Open `plugins/AdvancedBan/discord/config.yml` and paste it into `webhook-url`.
4. Restart your server (or run `/advancedban reload` once that's re-enabled).

Per-type configuration in `discord/config.yml`:

```yaml
messages:
  ban:       "{embed:ban}"          # use embed/ban.json
  tempban:   "{embed:tempban}"
  unban:     "{embed:unban}"
  mute:      ""                     # disable mute notifications
  warn:      "Player %player% was warned by %staff% for %reason%"   # plain text
```

Available placeholders: `%player%`, `%staff%`, `%reason%`, `%duration%`, `%id%`, `%uuid%`, `%type%`.

Edit `discord/embed/<type>.json` to customize colors, titles, descriptions, footers.
The format mirrors Discord Ban Announcer's, so existing DBA embed packs drop in cleanly.

---

## MySQL setup

Set `UseMySQL: true` in `config.yml`, then either edit `MySQL:` in `config.yml` or create
a separate `MySQL.yml` with the same structure:

```yaml
MySQL:
  IP: localhost
  DB-Name: advancedban
  Username: admin
  Password: superSecret
  Port: 3306
  Properties: 'verifyServerCertificate=false&useSSL=false&useUnicode=true&characterEncoding=utf8'
  Pool:
    MaximumPoolSize: 16     # default: max(4, cpus * 2)
    MinimumIdle: 2
    ConnectionTimeout: 10000
    IdleTimeout: 600000
    MaxLifetime: 1800000
    PrepStmtCacheSize: 250
    PrepStmtCacheSqlLimit: 2048
```

The pool tuning is optional — sensible defaults are applied if absent.

---

## Commands & permissions

Same as upstream AdvancedBan. Quick reference:

| Command                          | Permission         |
| -------------------------------- | ------------------ |
| `/ban <player> [reason]`         | `ab.ban.perma`     |
| `/tempban <player> <time> [r]`   | `ab.ban.temp`      |
| `/ipban <player> [reason]`       | `ab.ipban.perma`   |
| `/tempipban <player> <time> [r]` | `ab.ipban.temp`    |
| `/mute <player> [reason]`        | `ab.mute.perma`    |
| `/tempmute <player> <time> [r]`  | `ab.mute.temp`     |
| `/warn <player> [reason]`        | `ab.warn.perma`    |
| `/tempwarn <player> <time> [r]`  | `ab.warn.temp`     |
| `/kick <player> [reason]`        | `ab.kick.use`      |
| `/note <player> <note>`          | `ab.note.use`      |
| `/unban <player>`                | `ab.unban`         |
| `/unmute <player>`               | `ab.unmute`        |
| `/unwarn <id>`                   | `ab.unwarn`        |
| `/unnote <id>`                   | `ab.unnote`        |
| `/check <player>`                | `ab.check`         |
| `/history <player>`              | `ab.history`       |
| `/banlist [page]`                | `ab.banlist`       |
| `/advancedban`                   | (info)             |

Time format: `1s`, `5m`, `2h`, `7d`, `1w`, `1mo`.

---

## Building from source

See [BUILD.md](BUILD.md). TL;DR:

```sh
# Release build
mvn -B clean package

# Debug build (verbose SQL/HTTP/event logging)
mvn -Pdebug -B clean package
```

Requires JDK 21 and Maven 3.8+.

---

## API for developers

Two events you can subscribe to:

- `PunishmentEvent` — fired when a punishment is created
- `RevokePunishmentEvent` — fired when a punishment is revoked

Velocity: see [docs/VELOCITY-API.md](docs/VELOCITY-API.md).
Bukkit / Bungee: events live in `me.leoko.advancedban.bukkit.event` / `me.leoko.advancedban.bungee.event`
and extend the respective platform's `Event` base class.

---

## Troubleshooting

| Symptom                                                          | Fix                                                                                          |
| ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `Discord webhook notifications inactive (no webhook-url set)`    | Expected. Set `webhook-url` in `discord/config.yml`.                                         |
| `No offline permission support (LuckPerms not detected)`         | Install LuckPerms on your proxy if you need offline-permission checks; otherwise ignore.     |
| `Failed to load discord/config.yml` on startup                   | The file got corrupted or unreadable. Delete `plugins/AdvancedBan/discord/` and restart.     |
| `An unexpected error has occurred connecting to the database`    | Check MySQL credentials in `MySQL.yml`. Or run with `UseMySQL: false` to use local HSQLDB.   |
| Class-not-found / `ClassNotFoundException` on plugin load        | You probably installed the wrong jar. Use `*-Universal-*.jar` for Bukkit/Bungee and `*-Velocity-*.jar` for Velocity. |
| Discord embeds look wrong                                        | Edit `plugins/AdvancedBan/discord/embed/<type>.json` — same format as Discord Ban Announcer. |
| Something acts up and I want details                             | Build the debug variant (`mvn -Pdebug clean package`) and try again. Logs SQL + HTTP traces. |

---

## License

GPL-3.0 — inherited from the upstream project.

---

## Credits

- **Leoko** — original author of AdvancedBan
- **Leon (Grafkox_LP)** — fork maintainer (Velocity port, modernization, Discord integration)
- Embed file format inspired by **Discord Ban Announcer**
