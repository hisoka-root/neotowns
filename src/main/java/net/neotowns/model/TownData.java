package net.neotowns.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neotowns.model.enums.TaxType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TownData(
    NTId id,
    String name,
    UUID mayorUUID,
    Set<UUID> residentUUIDs,
    Set<UUID> assistantUUIDs,
    NTId stateId,
    BlockPos treasuryChestPos,
    ResourceKey<Level> treasuryWorld,
    long emeraldBalance,
    long dailyUpkeepPerChunk,
    long residentTaxEmeralds,
    TaxType residentTaxType,
    int maxClaims,
    boolean isOpen,
    boolean isPvpEnabled,
    boolean isFireSpread,
    boolean isMobSpawn,
    Map<String, PlotData> plots,
    long foundedEpochDay,
    String motd,
    TownPerms perms
) {
    public int claimCount() {
        return plots.size();
    }

    public boolean isMayor(UUID player) {
        return mayorUUID.equals(player);
    }

    public boolean isAssistant(UUID player) {
        return assistantUUIDs.contains(player);
    }

    public boolean isResident(UUID player) {
        return residentUUIDs.contains(player);
    }

    public boolean hasState() {
        return stateId != null;
    }
}
