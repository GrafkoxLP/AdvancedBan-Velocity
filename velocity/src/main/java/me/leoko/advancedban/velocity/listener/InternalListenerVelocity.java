package me.leoko.advancedban.velocity.listener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import me.leoko.advancedban.velocity.event.PunishmentEvent;
import me.leoko.advancedban.velocity.event.RevokePunishmentEvent;

import java.util.Collections;
import java.util.List;

/**
 * Backend-server plugin-message bridge. Exact wire-protocol match with the Bungee
 * implementation ({@code bungee/.../listener/InternalListener.java}) so Spigot servers
 * running the AdvancedBan-Bukkit plugin sync the same way regardless of proxy choice.
 */
public final class InternalListenerVelocity {

    public static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("advancedban:main");

    private final ProxyServer server;
    private final Universal universal = Universal.get();

    public InternalListenerVelocity(ProxyServer server) {
        this.server = server;
    }

    @Subscribe
    public void onPunish(PunishmentEvent e) {
        sendToBackends("Punish", Collections.singletonList(e.getPunishment().toString()));
    }

    @Subscribe
    public void onUnPunish(RevokePunishmentEvent e) {
        sendToBackends("Unpunish", Collections.singletonList(e.getPunishment().toString()));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;
        if (event.getSource() instanceof Player) return; // ignore client→server
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel = in.readUTF();
        if ("Punish".equals(channel)) {
            String message = in.readUTF();
            try {
                JsonObject punishment = universal.getGson().fromJson(message, JsonObject.class);
                new Punishment(
                        punishment.get("name").getAsString(),
                        UUIDManager.get().getUUID(punishment.get("uuid").getAsString()),
                        punishment.get("reason").getAsString(),
                        punishment.get("operator") != null ? punishment.get("operator").getAsString() : "CONSOLE",
                        PunishmentType.valueOf(punishment.get("punishmenttype").getAsString().toUpperCase()),
                        punishment.get("start") != null ? punishment.get("start").getAsLong() : TimeManager.getTime(),
                        TimeManager.getTime() + punishment.get("end").getAsLong(),
                        punishment.get("calculation") != null ? punishment.get("calculation").getAsString() : null,
                        -1
                ).create(punishment.get("silent") != null && punishment.get("silent").getAsBoolean());
                universal.log("A punishment was created using PluginMessaging listener.");
                universal.debug(punishment.toString());
            } catch (JsonSyntaxException | NullPointerException ex) {
                universal.log("An exception occurred while reading a punishment from plugin messaging channel.");
                universal.debug("Message: " + message);
                universal.debugException(ex);
            }
        } else {
            universal.debug("Unknown channel for tag \"AdvancedBan\": " + channel);
        }
    }

    public void sendToBackends(String subChannel, List<String> messages) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subChannel);
        messages.forEach(out::writeUTF);
        byte[] data = out.toByteArray();
        server.getAllServers().forEach(rs -> rs.sendPluginMessage(CHANNEL, data));
    }
}
