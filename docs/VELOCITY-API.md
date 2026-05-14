# AdvancedBan Velocity API

External plugins can hook into AdvancedBan on Velocity by subscribing to two events:

- `me.leoko.advancedban.velocity.event.PunishmentEvent` — fired when a punishment is created
- `me.leoko.advancedban.velocity.event.RevokePunishmentEvent` — fired when a punishment is revoked

Both are plain Velocity event POJOs (no base class) and are dispatched on Velocity's `EventManager`.

## Adding AdvancedBan as a build dependency

If you build with Maven, depend on `AdvancedBan-Core` (events live in `velocity` module, but the
`Punishment` object type they expose lives in core):

```xml
<dependency>
    <groupId>me.leoko.advancedban</groupId>
    <artifactId>AdvancedBan-Velocity</artifactId>
    <version>2.4.0</version>
    <scope>provided</scope>
</dependency>
```

Add a Velocity plugin dependency on `advancedban`:

```java
@Plugin(
    id = "my-plugin",
    dependencies = { @Dependency(id = "advancedban") }
)
public class MyPlugin { … }
```

## Subscribing to punishment events

```java
import com.velocitypowered.api.event.Subscribe;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.velocity.event.PunishmentEvent;
import me.leoko.advancedban.velocity.event.RevokePunishmentEvent;

public class MyListener {

    @Subscribe
    public void onPunish(PunishmentEvent event) {
        Punishment p = event.getPunishment();
        System.out.println(p.getOperator() + " punished " + p.getName()
                + " (" + p.getType() + ") for: " + p.getReason());
    }

    @Subscribe
    public void onRevoke(RevokePunishmentEvent event) {
        Punishment p = event.getPunishment();
        boolean batch = event.isMassClear();
        System.out.println("Revoked " + p.getType() + " #" + p.getId());
    }
}
```

Register the listener in your plugin's `ProxyInitializeEvent` handler:

```java
@Subscribe
public void onInit(ProxyInitializeEvent event) {
    server.getEventManager().register(this, new MyListener());
}
```

## What you can read from a `Punishment`

| Method                      | What it returns                                                  |
|-----------------------------|------------------------------------------------------------------|
| `getId()`                   | Punishment ID (database PK), `-1` until persisted                |
| `getName()`                 | Player name                                                      |
| `getUuid()`                 | Player UUID (no dashes)                                          |
| `getReason()`               | Punishment reason                                                |
| `getOperator()`             | Who issued the punishment (`"CONSOLE"` for console)              |
| `getType()`                 | `PunishmentType` (BAN, TEMP_BAN, IP_BAN, …, KICK, NOTE)         |
| `getStart()`                | Issue timestamp (epoch ms)                                       |
| `getEnd()`                  | Expiry timestamp (epoch ms), `-1` for permanent                  |
| `getDuration(boolean)`      | Human-readable duration (e.g. "3 days, 5 hours")                |
| `isExpired()`               | Whether a temp punishment has elapsed                            |

## Threading

Both events fire on whatever thread the punishment was created/revoked on, which is typically an
async thread (login flow, command dispatch). Don't assume the main thread.

## Compatibility note

Plugins targeting the Bungee version of AdvancedBan (`me.leoko.advancedban.bungee.event.PunishmentEvent`)
are **not source-compatible** with the Velocity version — Velocity events extend nothing, while Bungee
events extend `net.md_5.bungee.api.plugin.Event`. If you maintain a cross-platform plugin (e.g.
Discord Ban Announcer), you need a separate Velocity listener class.
