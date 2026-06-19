package net.neotowns.model;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neotowns.model.enums.PlotType;

import java.util.UUID;

public record PlotData(
    ChunkPos pos,
    ResourceKey<Level> world,
    NTId townId,
    PlotType type,
    UUID ownerUUID,
    long salePrice,
    boolean isEmbassy
) {
    public boolean isForSale() {
        return salePrice > 0;
    }

    public boolean isTownOwned() {
        return ownerUUID == null;
    }
}
