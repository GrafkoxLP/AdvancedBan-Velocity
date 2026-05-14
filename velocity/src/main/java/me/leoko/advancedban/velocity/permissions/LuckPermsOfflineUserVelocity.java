package me.leoko.advancedban.velocity.permissions;

import me.leoko.advancedban.utils.Permissionable;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;

import java.util.UUID;

/**
 * Offline-permission lookup via LuckPerms. Identical to the Bungee variant; lives in the
 * Velocity module so Velocity builds don't pull bungeecord-api transitively.
 */
public class LuckPermsOfflineUserVelocity implements Permissionable {

    private User permissionUser;

    public LuckPermsOfflineUserVelocity(String name) {
        final UserManager userManager = LuckPermsProvider.get().getUserManager();
        final UUID uuid = userManager.lookupUniqueId(name).join();
        if (uuid != null) {
            this.permissionUser = userManager.loadUser(uuid).join();
        }
    }

    @Override
    public boolean hasPermission(String permission) {
        return permissionUser != null && permissionUser.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }
}
