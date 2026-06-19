package net.neotowns.event;

import net.minecraft.world.level.ChunkPos;
import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

public class ClaimEvent extends Event {
    private final TownData town;
    private final ChunkPos chunk;
    private final boolean claiming;

    public ClaimEvent(TownData town, ChunkPos chunk, boolean claiming) {
        this.town = town;
        this.chunk = chunk;
        this.claiming = claiming;
    }

    public TownData getTown() { return town; }
    public ChunkPos getChunk() { return chunk; }
    public boolean isClaiming() { return claiming; }
}
