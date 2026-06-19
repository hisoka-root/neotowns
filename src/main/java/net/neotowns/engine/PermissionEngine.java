package net.neotowns.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.model.TownData;
import net.neotowns.model.enums.PermFlag;
import net.neotowns.permissions.PermissionsIntegration;
import net.neotowns.permissions.PermissionsIntegrationRegistry;

import java.util.UUID;

public final class PermissionEngine {

    private PermissionEngine() {}

    public static boolean canRunCommand(ServerPlayer player, String node) {
        PermissionsIntegration perms = PermissionsIntegrationRegistry.get();
        return perms.hasPermission(player, node)
            || perms.hasPermission(player, "neotowns.admin");
    }

    public static boolean canAct(ServerPlayer player, UUID townId, BlockPos pos, PermFlag flag) {
        PermissionsIntegration perms = PermissionsIntegrationRegistry.get();

        if (perms.hasPermission(player, "neotowns.admin")) return true;
        if (perms.hasPermission(player, "neotowns.bypass.protection")) return true;

        return internalCheck(player, townId, pos, flag);
    }

    private static boolean internalCheck(ServerPlayer player, UUID townId, BlockPos pos, PermFlag flag) {
        TownData town = NeoTownsCache.getTown(townId);
        if (town == null) return true;

        var chunkKey = player.serverLevel().dimension().location().toString()
            + "|" + (pos.getX() >> 4) + "|" + (pos.getZ() >> 4);
        var plot = town.plots().get(chunkKey);
        if (plot != null && plot.ownerUUID() != null && plot.ownerUUID().equals(player.getUUID())) {
            return true;
        }

        boolean isResident = town.isResident(player.getUUID());
        if (!isResident) return false;

        return town.perms().has(flag);
    }

    public static boolean canActByUUID(UUID playerUUID, UUID townId, BlockPos pos, PermFlag flag) {
        TownData town = NeoTownsCache.getTown(townId);
        if (town == null) return true;
        return town.isResident(playerUUID) && town.perms().has(flag);
    }
}
