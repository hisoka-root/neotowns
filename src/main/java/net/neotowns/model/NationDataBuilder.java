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

public class NationDataBuilder {
    public NTId id;
    public String name;
    public UUID leaderUUID;
    public Set<NTId> stateIds;
    public BlockPos treasuryChestPos;
    public ResourceKey<Level> treasuryWorld;
    public long nationTaxEmeralds;
    public TaxType nationTaxType;
    public DiplomacyMap diplomacy;
    public GovernmentType governmentType;
    public Map<String, String> laws;
    public String constitution;
    public Map<UUID, String> cabinet;
    public String ideology;
    public String anthem;
    public long foundedEpochDay;

    public NationDataBuilder() {}

    public NationDataBuilder(NationData source) {
        this.id = source.id();
        this.name = source.name();
        this.leaderUUID = source.leaderUUID();
        this.stateIds = source.stateIds();
        this.treasuryChestPos = source.treasuryChestPos();
        this.treasuryWorld = source.treasuryWorld();
        this.nationTaxEmeralds = source.nationTaxEmeralds();
        this.nationTaxType = source.nationTaxType();
        this.diplomacy = source.diplomacy();
        this.governmentType = source.governmentType();
        this.laws = source.laws();
        this.constitution = source.constitution();
        this.cabinet = source.cabinet();
        this.ideology = source.ideology();
        this.anthem = source.anthem();
        this.foundedEpochDay = source.foundedEpochDay();
    }

    public NationData build() {
        return new NationData(id, name, leaderUUID, stateIds,
            treasuryChestPos, treasuryWorld, nationTaxEmeralds, nationTaxType,
            diplomacy, governmentType, laws, constitution, cabinet,
            ideology, anthem, foundedEpochDay);
    }
}
