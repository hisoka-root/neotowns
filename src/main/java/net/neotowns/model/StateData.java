package net.neotowns.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neotowns.model.enums.GovernmentType;
import net.neotowns.model.enums.TaxType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record StateData(
    NTId id,
    String name,
    UUID chancellorUUID,
    Set<NTId> townIds,
    NTId nationId,
    BlockPos treasuryChestPos,
    ResourceKey<Level> treasuryWorld,
    long stateTaxEmeralds,
    TaxType stateTaxType,
    GovernmentType governmentType,
    Map<String, String> laws,
    String constitution,
    Map<UUID, String> cabinet,
    long foundedEpochDay
) {
    public boolean hasNation() {
        return nationId != null;
    }

    public boolean isChancellor(UUID player) {
        return chancellorUUID.equals(player);
    }
}
