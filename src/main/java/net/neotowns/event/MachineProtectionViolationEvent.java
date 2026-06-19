package net.neotowns.event;

import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.Event;

import java.util.UUID;

public class MachineProtectionViolationEvent extends Event {
    private final UUID townId;
    private final ChunkPos chunkPos;
    private final String dimension;

    public MachineProtectionViolationEvent(UUID townId, ChunkPos chunkPos, String dimension) {
        this.townId = townId;
        this.chunkPos = chunkPos;
        this.dimension = dimension;
    }

    public UUID getTownId() { return townId; }
    public ChunkPos getChunkPos() { return chunkPos; }
    public String getDimension() { return dimension; }
}
