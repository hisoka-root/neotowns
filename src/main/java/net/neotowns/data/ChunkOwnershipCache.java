package net.neotowns.data;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkOwnershipCache {

    private static final Map<String, Map<Long, UUID>> cache = new ConcurrentHashMap<>();

    private ChunkOwnershipCache() {}

    public static void loadAll() {
        cache.clear();
        DatabaseManager.loadAllChunkClaims().forEach((key, townId) -> {
            String[] parts = key.split("\\|");
            cache.computeIfAbsent(parts[0], k -> new ConcurrentHashMap<>())
                 .put(Long.parseLong(parts[1]), townId);
        });
    }

    public static UUID getOwner(Level level, ChunkPos pos) {
        String dim = level.dimension().location().toString();
        Map<Long, UUID> dimCache = cache.get(dim);
        return dimCache != null ? dimCache.get(pos.toLong()) : null;
    }

    public static void setOwner(Level level, ChunkPos pos, UUID townId) {
        String dim = level.dimension().location().toString();
        cache.computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
             .put(pos.toLong(), townId);
        DatabaseManager.setChunkOwner(dim, pos, townId);
    }

    public static void clearOwner(Level level, ChunkPos pos) {
        String dim = level.dimension().location().toString();
        Map<Long, UUID> dimCache = cache.get(dim);
        if (dimCache != null) {
            dimCache.remove(pos.toLong());
        }
        DatabaseManager.clearChunkOwner(dim, pos);
    }

    public static int countClaims(UUID townId) {
        return (int) cache.values().stream()
            .flatMap(m -> m.values().stream())
            .filter(townId::equals)
            .count();
    }

    public static Map<Long, UUID> getSnapshot(String dimension) {
        var dimCache = cache.get(dimension);
        return dimCache != null ? Map.copyOf(dimCache) : Map.of();
    }
}
