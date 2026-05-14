package me.leoko.advancedban.velocity.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import me.leoko.advancedban.Universal;

/**
 * Mute enforcement on Velocity. Uses the deprecated-but-still-functional
 * {@link PlayerChatEvent} for chat denial; the proxy still receives the chat
 * event for muting purposes even on signed chat versions, the deprecation is
 * about modifying message content (which we don't do).
 */
public final class ChatListenerVelocity {

    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.EARLY)
    public void onChat(PlayerChatEvent event) {
        if (Universal.get().getMethods().callChat(event.getPlayer())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player p)) return;
        // callCMD expects a leading slash — Velocity strips it from getCommand().
        if (Universal.get().getMethods().callCMD(p, "/" + event.getCommand())) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }
}
