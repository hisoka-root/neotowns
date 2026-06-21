package net.neotowns.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neotowns.model.NationData;
import net.neotowns.model.StateData;
import net.neotowns.model.TownData;

public interface MapIntegration {
    String modId();

    void onChunkClaimed(TownData town, ChunkPos chunk, ServerLevel level);
    void onChunkUnclaimed(TownData town, ChunkPos chunk, ServerLevel level);
    void onTownFounded(TownData town);
    void onTownDisbanded(TownData town);
    void onTownRenamed(TownData town, String oldName);

    void onStateUpdated(StateData state);
    void onNationUpdated(NationData nation);

    void drawTownHomeMarker(TownData town);
    void drawStateCapitalMarker(StateData state);
    void drawNationCapitalMarker(NationData nation);
    void removeMarker(String markerId);

    void rebuildAll(MinecraftServer server);
    void shutdown();
}
