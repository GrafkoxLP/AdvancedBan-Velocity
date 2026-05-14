package me.leoko.advancedban.bukkit.listener;

import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles login-time ban checks and on-disconnect cache cleanup.
 */
public class ConnectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onConnect(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            UUIDManager.get().supplyInternUUID(event.getName(), event.getUniqueId());
            String result = Universal.get().callConnection(event.getName(), event.getAddress().getHostAddress());
            if (result != null) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, result);
            }
        }
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent event) {
        PunishmentManager.get().discard(event.getPlayer().getName());
    }
}
