package net.neotowns.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.ChunkOwnershipCache;
import net.neotowns.engine.PermissionEngine;
import net.neotowns.model.enums.PermFlag;
import net.neotowns.util.Messenger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.UUID;

@EventBusSubscriber(modid = "neotowns")
public final class ChunkProtectionHandler {

    private ChunkProtectionHandler() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        var actor = event.getPlayer();

        if (actor instanceof ServerPlayer player && !(actor instanceof FakePlayer)) {
            ChunkPos chunk = new ChunkPos(event.getPos());
            UUID townId = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
            if (townId == null) return;
            if (!PermissionEngine.canAct(player, townId, event.getPos(), PermFlag.DESTROY)) {
                event.setCanceled(true);
                Messenger.deny(player, "You cannot break blocks here.");
            }
            return;
        }

        if (actor instanceof FakePlayer fakePlayer) {
            ChunkPos chunk = new ChunkPos(event.getPos());
            UUID townId = ChunkOwnershipCache.getOwner(fakePlayer.serverLevel(), chunk);
            if (townId == null) return;
            if (!NeoTownsConfig.get().isAllowMachinesInTown()) {
                event.setCanceled(true);
                return;
            }
            UUID ownerUUID = FakePlayerOwnerResolver.resolve(fakePlayer);
            if (ownerUUID != null) {
                var owner = fakePlayer.server.getPlayerList().getPlayer(ownerUUID);
                if (owner != null && PermissionEngine.canAct(owner, townId, event.getPos(), PermFlag.DESTROY)) {
                    return;
                }
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChunkPos chunk = new ChunkPos(event.getPos());
        UUID townId = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
        if (townId == null) return;
        if (!PermissionEngine.canAct(player, townId, event.getPos(), PermFlag.BUILD)) {
            event.setCanceled(true);
            Messenger.deny(player, "You cannot place blocks here.");
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChunkPos chunk = new ChunkPos(event.getPos());
        UUID townId = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
        if (townId == null) return;
        if (!PermissionEngine.canAct(player, townId, event.getPos(), PermFlag.INTERACT)) {
            event.setCanceled(true);
            Messenger.deny(player, "You cannot interact here.");
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ChunkPos chunk = new ChunkPos(event.getPos());
        UUID townId = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
        if (townId == null) return;
        if (!PermissionEngine.canAct(player, townId, event.getPos(), PermFlag.ITEM_USE)) {
            event.setCanceled(true);
            Messenger.deny(player, "You cannot use items here.");
        }
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() == null) return;
        var level = event.getEntity().level();
        ChunkPos chunk = new ChunkPos(event.getEntity().blockPosition());
        if (ChunkOwnershipCache.getOwner(level, chunk) != null) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        var level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> {
            ChunkPos chunk = new ChunkPos(pos);
            return ChunkOwnershipCache.getOwner(level, chunk) != null;
        });
    }

    @SubscribeEvent
    public static void onBlockBurn(BlockEvent.NeighborNotifyEvent event) {
        var level = event.getLevel();
        var state = level.getBlockState(event.getPos());
        if (state.is(net.minecraft.world.level.block.Blocks.FIRE)
            || state.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) {
            ChunkPos chunk = new ChunkPos(event.getPos());
            if (ChunkOwnershipCache.getOwner((net.minecraft.world.level.Level) level, chunk) != null) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPvP(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer attacker)) return;
        var target = event.getEntity();
        ChunkPos chunk = new ChunkPos(target.blockPosition());
        UUID townId = ChunkOwnershipCache.getOwner(target.level(), chunk);
        if (townId == null) return;
        var town = net.neotowns.data.NeoTownsCache.getTown(townId);
        if (town == null) return;
        if (!PermissionEngine.canAct(attacker, townId, target.blockPosition(), PermFlag.PVP)) {
            event.setNewDamage(0f);
            Messenger.deny(attacker, "PvP is disabled here.");
        }
    }

    @SubscribeEvent
    public static void onPistonPre(PistonEvent.Pre event) {
        if (!NeoTownsConfig.get().isDenyPistonsAcrossClaims()) return;
        var level = (net.minecraft.world.level.Level) event.getLevel();
        var helper = event.getStructureHelper();
        if (!helper.resolve()) return;
        var affected = helper.getToPush();
        if (affected == null || affected.isEmpty()) return;
        ChunkPos firstChunk = null;
        for (var pos : affected) {
            ChunkPos cp = new ChunkPos(pos);
            if (firstChunk == null) {
                firstChunk = cp;
            } else if (!cp.equals(firstChunk)) {
                UUID owner1 = ChunkOwnershipCache.getOwner(level, firstChunk);
                UUID owner2 = ChunkOwnershipCache.getOwner(level, cp);
                if (owner1 != null && owner2 != null && !owner1.equals(owner2)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }
}
