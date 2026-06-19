package net.neotowns.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.ChunkOwnershipCache;
import net.neotowns.event.MachineProtectionViolationEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "neotowns")
public final class ChunkSnapshotWatchdog {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Long, Long> snapshots = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private ChunkSnapshotWatchdog() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!(chunk.getLevel() instanceof ServerLevel level)) return;
        var pos = chunk.getPos();
        if (ChunkOwnershipCache.getOwner(level, pos) == null) return;
        snapshots.put(pos.toLong(), hashChunk(chunk));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!NeoTownsConfig.get().isWatchdogEnabled()) return;

        tickCounter++;
        int interval = NeoTownsConfig.get().getWatchdogTickInterval();
        if (tickCounter < interval) return;
        tickCounter = 0;

        for (var serverLevel : event.getServer().getAllLevels()) {
            if (!(serverLevel instanceof ServerLevel level)) continue;
            String dim = level.dimension().location().toString();

            for (var entry : ChunkOwnershipCache.getSnapshot(dim).entrySet()) {
                long chunkLong = entry.getKey();
                UUID townId = entry.getValue();
                int cx = (int) (chunkLong >> 32);
                int cz = (int) (chunkLong & 0xFFFFFFFFL);
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                long currentHash = hashChunk(chunk);
                Long previous = snapshots.get(chunkLong);
                if (previous != null && previous != currentHash) {
                    LOGGER.warn("[NeoTowns] Watchdog: chunk [{},{}] modified in town {} without event", cx, cz, townId);
                    snapshots.put(chunkLong, currentHash);
                    NeoForge.EVENT_BUS.post(new MachineProtectionViolationEvent(
                        townId, new ChunkPos(cx, cz), dim));
                }
            }
        }
    }

    private static long hashChunk(LevelChunk chunk) {
        long hash = 0x811C9DC5L;
        var cp = chunk.getPos();
        int minX = cp.getMinBlockX(), maxX = cp.getMaxBlockX();
        int minZ = cp.getMinBlockZ(), maxZ = cp.getMaxBlockZ();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight() - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int stateId = net.minecraft.world.level.block.Block.getId(chunk.getBlockState(new BlockPos(x, y, z)));
                    hash ^= (stateId & 0xFF);
                    hash *= 0x01000193L;
                    hash ^= ((stateId >> 8) & 0xFF);
                    hash *= 0x01000193L;
                }
            }
        }
        return hash;
    }
}
