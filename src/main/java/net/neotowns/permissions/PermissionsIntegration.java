package net.neotowns.permissions;

import net.minecraft.server.level.ServerPlayer;

public interface PermissionsIntegration {
    String modId();
    boolean hasPermission(ServerPlayer player, String node);
}
