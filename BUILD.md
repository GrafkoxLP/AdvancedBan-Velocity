# Building AdvancedBan

This fork supports Bukkit/Spigot, BungeeCord, and Velocity from a single Maven reactor.

## Prerequisites

- **JDK 21** (Temurin / Adoptium recommended)
- **Maven 3.8+**
- Internet access (first build pulls dependencies from Maven Central, PaperMC, Spigot snapshots, CodeMC, JitPack)

Verify with:

```sh
java -version    # should report 21.x
mvn -v           # should report Maven 3.8+ and Java 21
```

## Release build

From the repository root:

```sh
mvn -B clean package
```

Outputs (in `bundle/target/`):

- `AdvancedBan-Universal-2.4.0-RELEASE.jar` — drop into a **Bukkit/Spigot or BungeeCord** server
- `AdvancedBan-Velocity-2.4.0-RELEASE.jar` — drop into a **Velocity** proxy

Both bundles also include the Discord webhook integration in the DBA-compatible format.

## Debug build

```sh
mvn -Pdebug -B clean package
```

Outputs the same jars but tagged `-DEBUG` instead of `-RELEASE`. The DEBUG variant:

- Forces verbose logging on regardless of the `Debug:` config flag.
- Logs **every** SQL query (with parameters) — useful when MySQL behaves oddly.
- Logs the HTTP status code of every Discord webhook POST.
- Prints full stack traces (instead of single-line summaries) on errors.
- Prints a `!!! DEBUG BUILD ACTIVE !!!` banner on plugin enable.

Use the DEBUG jar to reproduce a bug, capture the log, and revert to the RELEASE jar afterwards.

## What gets built

| Module       | Artifact                              | Purpose                                            |
|--------------|---------------------------------------|----------------------------------------------------|
| `core`       | `AdvancedBan-Core`                    | Server-independent business logic                  |
| `bukkit`     | `AdvancedBan-Bukkit`                  | Bukkit/Spigot platform implementation              |
| `bungee`     | `AdvancedBan-Bungee`                  | BungeeCord platform implementation                 |
| `velocity`   | `AdvancedBan-Velocity`                | Velocity platform implementation                   |
| `bundle`     | `AdvancedBan-Universal-*` + `AdvancedBan-Velocity-*` | Shaded fat-jars users install   |

The per-module jars (everything except `bundle`) are intermediate artifacts. Users only need the `bundle` outputs.

## Skipping tests

```sh
mvn -B -DskipTests clean package
```

## Common errors

- **`Source option 8 is no longer supported`** — you're running Java 8 / 11 / 17. Switch to Java 21.
- **`Could not find artifact com.velocitypowered:velocity-api:jar:3.3.0-SNAPSHOT`** — the PaperMC repo is unreachable. Check your network or repo configuration.
- **`Could not find artifact org.spigotmc:spigot-api:jar:1.21.1-R0.1-SNAPSHOT`** — the Spigot snapshot version may have rolled. Bump in `bukkit/pom.xml` to a current snapshot.
