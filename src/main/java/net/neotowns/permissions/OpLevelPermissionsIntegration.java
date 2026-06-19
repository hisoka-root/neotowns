package net.neotowns.permissions;

import net.minecraft.server.level.ServerPlayer;

public class OpLevelPermissionsIntegration implements PermissionsIntegration {

    @Override
    public boolean hasPermission(ServerPlayer player, String node) {
        if (node.equals("neotowns.admin")) return player.hasPermissions(4);
        if (node.equals("neotowns.moderator")) return player.hasPermissions(3);
        if (node.equals("neotowns.bypass.protection")) return player.hasPermissions(2);
        if (node.startsWith("neotowns.command.")) return true;
        return false;
    }

    @Override
    public String modId() {
        return "op_level";
    }
}
