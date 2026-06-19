package net.neotowns.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neotowns.model.enums.DiplomacyStatus;
import net.neotowns.model.enums.GovernmentType;
import net.neotowns.model.enums.TaxType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record NationData(
    NTId id,
    String name,
    UUID leaderUUID,
    Set<NTId> stateIds,
    BlockPos treasuryChestPos,
    ResourceKey<Level> treasuryWorld,
    long nationTaxEmeralds,
    TaxType nationTaxType,
    DiplomacyMap diplomacy,
    GovernmentType governmentType,
    Map<String, String> laws,
    String constitution,
    Map<UUID, String> cabinet,
    String ideology,
    String anthem,
    long foundedEpochDay
) {
    public DiplomacyStatus getDiplomaticStatus(NTId other) {
        return diplomacy.getStatus(other);
    }

    public boolean isLeader(UUID player) {
        return leaderUUID.equals(player);
    }
}
