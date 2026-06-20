package net.neotowns.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neotowns.model.enums.GovernmentType;
import net.neotowns.model.enums.TaxType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StateDataBuilder {
    public NTId id;
    public String name;
    public UUID chancellorUUID;
    public Set<NTId> townIds;
    public NTId nationId;
    public BlockPos treasuryChestPos;
    public ResourceKey<Level> treasuryWorld;
    public long stateTaxEmeralds;
    public TaxType stateTaxType;
    public GovernmentType governmentType;
    public Map<String, String> laws;
    public String constitution;
    public Map<UUID, String> cabinet;
    public long foundedEpochDay;

    public StateDataBuilder() {}

    public StateDataBuilder(StateData source) {
        this.id = source.id();
        this.name = source.name();
        this.chancellorUUID = source.chancellorUUID();
        this.townIds = source.townIds();
        this.nationId = source.nationId();
        this.treasuryChestPos = source.treasuryChestPos();
        this.treasuryWorld = source.treasuryWorld();
        this.stateTaxEmeralds = source.stateTaxEmeralds();
        this.stateTaxType = source.stateTaxType();
        this.governmentType = source.governmentType();
        this.laws = source.laws();
        this.constitution = source.constitution();
        this.cabinet = source.cabinet();
        this.foundedEpochDay = source.foundedEpochDay();
    }

    public StateData build() {
        return new StateData(id, name, chancellorUUID, townIds, nationId,
            treasuryChestPos, treasuryWorld, stateTaxEmeralds, stateTaxType,
            governmentType, laws, constitution, cabinet, foundedEpochDay);
    }
}
