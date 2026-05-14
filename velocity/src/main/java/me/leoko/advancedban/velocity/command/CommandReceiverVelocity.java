package me.leoko.advancedban.velocity.command;

import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.leoko.advancedban.manager.CommandManager;
import me.leoko.advancedban.utils.Command;

import java.util.Collections;
import java.util.List;

/**
 * Wires a Velocity {@link RawCommand} to AdvancedBan's core {@link CommandManager#onCommand}.
 *
 * Uses {@link RawCommand} (not {@code SimpleCommand}) because the core command parser regex-matches
 * against the joined arg string and expects whitespace-split args; {@code RawCommand} gives us the
 * raw line so we can replicate the BungeeCord behaviour exactly.
 */
public final class CommandReceiverVelocity implements RawCommand {

    private final ProxyServer server;
    private final String name;
    private final String permission;

    public CommandReceiverVelocity(ProxyServer server, String name, String permission) {
        this.server = server;
        this.name = name;
        this.permission = permission;
    }

    @Override
    public void execute(Invocation invocation) {
        String raw = invocation.arguments();
        String[] args = raw.isEmpty() ? new String[0] : raw.split(" ", -1);

        // Preserve exact-case player names like BungeeCord's CommandReceiverBungee does.
        if (args.length > 0) {
            String playerName = args[0];
            args[0] = server.getPlayer(playerName).map(Player::getUsername).orElse(playerName);
        }

        CommandManager.get().onCommand(invocation.source(), name, args);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        Command cmd = Command.getByName(name);
        if (cmd == null) return Collections.emptyList();
        if (cmd.getPermission() != null && !invocation.source().hasPermission(cmd.getPermission())) {
            return Collections.emptyList();
        }
        String raw = invocation.arguments();
        String[] args = raw.isEmpty() ? new String[]{""} : raw.split(" ", -1);
        return cmd.getTabCompleter().onTabComplete(invocation.source(), args);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return permission == null || invocation.source().hasPermission(permission);
    }
}
