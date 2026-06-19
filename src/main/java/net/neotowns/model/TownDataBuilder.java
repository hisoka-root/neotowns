package net.neotowns.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neotowns.model.enums.TaxType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TownDataBuilder {
    public NTId id;
    public String name;
    public UUID mayorUUID;
    public Set<UUID> residentUUIDs;
    public Set<UUID> assistantUUIDs;
    public NTId stateId;
    public BlockPos treasuryChestPos;
    public ResourceKey<Level> treasuryWorld;
    public long emeraldBalance;
    public long dailyUpkeepPerChunk;
    public long residentTaxEmeralds;
    public TaxType taxType;
    public int maxClaims;
    public boolean isOpen;
    public boolean isPvpEnabled;
    public boolean isFireSpread;
    public boolean isMobSpawn;
    public Map<String, PlotData> plots;
    public long foundedEpochDay;
    public String motd;
    public TownPerms perms;

    public TownDataBuilder() {}

    public TownDataBuilder(TownData source) {
        this.id = source.id();
        this.name = source.name();
        this.mayorUUID = source.mayorUUID();
        this.residentUUIDs = source.residentUUIDs();
        this.assistantUUIDs = source.assistantUUIDs();
        this.stateId = source.stateId();
        this.treasuryChestPos = source.treasuryChestPos();
        this.treasuryWorld = source.treasuryWorld();
        this.emeraldBalance = source.emeraldBalance();
        this.dailyUpkeepPerChunk = source.dailyUpkeepPerChunk();
        this.residentTaxEmeralds = source.residentTaxEmeralds();
        this.taxType = source.residentTaxType();
        this.maxClaims = source.maxClaims();
        this.isOpen = source.isOpen();
        this.isPvpEnabled = source.isPvpEnabled();
        this.isFireSpread = source.isFireSpread();
        this.isMobSpawn = source.isMobSpawn();
        this.plots = source.plots();
        this.foundedEpochDay = source.foundedEpochDay();
        this.motd = source.motd();
        this.perms = source.perms();
    }

    public TownData build() {
        return new TownData(
            id, name, mayorUUID, residentUUIDs, assistantUUIDs, stateId,
            treasuryChestPos, treasuryWorld, emeraldBalance,
            dailyUpkeepPerChunk, residentTaxEmeralds, taxType, maxClaims,
            isOpen, isPvpEnabled, isFireSpread, isMobSpawn,
            plots, foundedEpochDay, motd, perms
        );
    }
}
