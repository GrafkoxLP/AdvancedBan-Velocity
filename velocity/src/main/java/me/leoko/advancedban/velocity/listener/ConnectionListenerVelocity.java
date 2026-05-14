package me.leoko.advancedban.velocity.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Login-time ban check + on-disconnect cache cleanup.
 *
 * The ban check returns an {@link EventTask#async} so Velocity holds the connection
 * until the database lookup is finished — same semantics as Bungee's
 * {@code registerIntent}/{@code completeIntent} pair, but via the modern Velocity
 * API.
 */
public final class ConnectionListenerVelocity {

    @Subscribe(order = PostOrder.EARLY)
    public EventTask onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) return null;
        Player p = event.getPlayer();

        // Cache the intern UUID up front — same as Bungee.
        UUIDManager.get().supplyInternUUID(p.getUsername(), p.getUniqueId());

        String ip = p.getRemoteAddress().getAddress().getHostAddress();

        return EventTask.async(() -> {
            String result = Universal.get().callConnection(p.getUsername(), ip);
            if (result != null) {
                event.setResult(LoginEvent.ComponentResult.denied(
                        LegacyComponentSerializer.legacySection().deserialize(result)));
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Universal.get().getMethods().runAsync(() ->
                PunishmentManager.get().discard(event.getPlayer().getUsername()));
    }
}
