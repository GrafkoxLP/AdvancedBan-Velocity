# Building AdvancedBan

This fork supports Bukkit/Spigot, BungeeCord, and Velocity from a single Maven reactor.

## Prerequisites

- **JDK 21** (Temurin / Adoptium recommended)
- **Maven 3.8+**
- Internet access (first build pulls dependencies from Maven Central, PaperMC, Spigot Hub, JitPack)

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

Both bundles include the Discord webhook integration in the DBA-compatible format.

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

## Skipping tests

```sh
mvn -B -DskipTests clean package
```

## What gets built

| Module       | Artifact                                              | Purpose                                            |
|--------------|-------------------------------------------------------|----------------------------------------------------|
| `core`       | `AdvancedBan-Core`                                    | Server-independent business logic                  |
| `bukkit`     | `AdvancedBan-Bukkit`                                  | Bukkit/Spigot platform implementation              |
| `bungee`     | `AdvancedBan-Bungee`                                  | BungeeCord platform implementation                 |
| `velocity`   | `AdvancedBan-Velocity`                                | Velocity platform implementation                   |
| `bundle`     | `AdvancedBan-Universal-*` + `AdvancedBan-Velocity-*`  | Shaded fat-jars users install                      |

The per-module jars (everything except `bundle`) are intermediate artifacts. Users only need the `bundle` outputs.

The empty `bundle/target/AdvancedBan-Bundle-*.jar` file you'll see next to the real outputs
is a side-effect of Maven's default jar plugin — ignore it, don't ship it.

## Where each dependency comes from

| Dependency                                  | Repository      | Why                                                                  |
|---------------------------------------------|-----------------|----------------------------------------------------------------------|
| `org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT` | Spigot Hub   | Spigot only publishes snapshots, and only to their own Nexus         |
| `net.md-5:bungeecord-api:1.21-R0.4`         | Maven Central   | Bungee has real RELEASE versions on Central — no SNAPSHOT needed     |
| `com.velocitypowered:velocity-api:3.3.0-SNAPSHOT` | PaperMC   | Official Velocity API repo                                           |
| `com.github.MilkBowl:VaultAPI`              | JitPack         | Vault is published as a GitHub source via JitPack                    |
| `org.bstats:bstats-*:3.1.0`                 | Maven Central   | bStats publishes to Central directly                                 |
| HikariCP, hsqldb, slf4j, commons-io, gson, JUnit | Maven Central | Standard libraries                                                  |

**No CodeMC, no Sonatype OSS Snapshots.** Both have been removed because:
- CodeMC's mirror was occasionally serving truncated artifacts for `net.md-5:*`
- Sonatype OSS Snapshots is being decommissioned in favor of the Central Portal

## Common errors

- **`Source option 8 is no longer supported`** — you're running Java 8 / 11 / 17. Switch to Java 21.
- **`Could not find artifact com.velocitypowered:velocity-api:jar:3.3.0-SNAPSHOT`** — the PaperMC repo is unreachable from your network. Check connectivity, or try again later.
- **`Could not find artifact org.spigotmc:spigot-api:jar:1.21.1-R0.1-SNAPSHOT`** — the Spigot snapshot version may have rolled to a newer one. Bump `bukkit/pom.xml` to the current snapshot version listed at https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/
- **`Could not find artifact net.md-5:bungeecord-api:jar:1.21-R0.4`** — extremely unlikely (Central never deletes releases), but if it does happen: bump to whatever's listed at https://central.sonatype.com/artifact/net.md-5/bungeecord-api/versions
- **`Premature end of Content-Length delimited message body`** — a mirror is serving a corrupted file. Usually transient. Clear the affected artifact from your local Maven cache (`~/.m2/repository/<groupId>/<artifactId>`) and retry. If it persists in CI, check the GitHub Actions log to see which repo is misbehaving.

## Bumping the version

Maven can update all `pom.xml` files in one shot:

```sh
mvn versions:set -DnewVersion=2.4.1 -DgenerateBackupPoms=false
```

After that, **manually** update the Velocity `@Plugin` annotation in
`velocity/src/main/java/me/leoko/advancedban/velocity/VelocityMain.java`:

```java
@Plugin(
        id = "advancedban",
        name = "AdvancedBan",
        version = "2.4.1",   // <-- here
        ...
)
```

Maven cannot rewrite this string because it's an annotation literal. Then commit, tag, push:

```sh
git add -A
git commit -m "Release 2.4.1"
git tag v2.4.1
git push origin master --tags
```

## Publishing a release

The CI workflow `.github/workflows/release.yml` builds and attaches the jars automatically.

**Trigger via GitHub UI:**

1. Repo → **Releases** → **Draft a new release**
2. Pick the tag you pushed (e.g. `v2.4.1`)
3. Title + release notes
4. **Publish release**
5. Wait ~1 minute for the workflow to finish — the `AdvancedBan-Universal-*-RELEASE.jar`
   and `AdvancedBan-Velocity-*-RELEASE.jar` will appear as release assets.

**Trigger manually from the Actions tab:**

1. Repo → **Actions** → **Release** → **Run workflow**
2. Optionally fill in `release_tag` (e.g. `v2.4.1`) to attach jars to an existing release.
   Leave empty to just produce the jars as a workflow artifact.
