package net.neotowns.config;

import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;

public final class NeoTownsConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("neotowns-server.toml");

    private int townFoundingCost = 64;
    private int chunkClaimCost = 16;
    private int outpostClaimMultiplier = 5;
    private int townUpkeepPerChunk = 1;
    private int stateFoundingCost = 512;
    private int stateUpkeepPerTown = 32;
    private int nationFoundingCost = 4096;
    private int nationUpkeepPerState = 128;
    private int startingGrantEmeralds = 32;
    private int baseClaims = 16;
    private int claimsPerResident = 8;
    private int maxClaimsHardCap = 1024;
    private int debtGraceDays = 3;
    private boolean upkeepUseMinecraftDays = true;
    private int upkeepMinecraftDayInterval = 1;
    private int upkeepRealTimeHours = 24;
    private boolean allowUnaffiliatedTowns = true;
    private int minTownsForState = 3;
    private int invitationWindowHours = 48;
    private int minStatesForNation = 2;
    private int warMinDurationHours = 24;
    private int warWarningPeriodHours = 24;
    private int vassalTributePercentage = 10;
    private int warDeclarationCost = 256;
    private int warSurrenderPenalty = 128;
    private int truceCost = 64;
    private int govtypeChangeCost = 256;
    private int electionIntervalRealDays = 30;
    private int votingWindowHours = 48;
    private boolean allowReelection = true;
    private int maxConsecutiveTerms = 3;
    private int noConfidenceThresholdPct = 60;
    private int coupThresholdPct = 75;
    private boolean wildernessBuildDefault = true;
    private boolean allowMachinesInTown = false;
    private boolean watchdogEnabled = true;
    private int watchdogTickInterval = 200;
    private boolean watchdogRollback = false;
    private boolean denyCreateContraptions = true;
    private boolean denyPistonsAcrossClaims = true;
    private String preferredIntegration = "auto";
    private int spawnCooldownSeconds = 30;
    private int spawnWarmupSeconds = 3;
    private boolean allowSpawnInCombat = false;
    private String dbType = "sqlite";
    private String dbPath = "world/neotowns/neotowns.db";
    private String dbHost = "localhost:3306";
    private String dbName = "neotowns";
    private String dbUser = "neotowns";
    private String dbPass = "";
    private int dbPoolSize = 10;
    private int updateDebounceMs = 2000;
    private double townFillOpacity = 0.35;

    private static final NeoTownsConfig INSTANCE = new NeoTownsConfig();

    private NeoTownsConfig() {}

    public static NeoTownsConfig load() {
        LOGGER.info("[NeoTowns] Loading config from {}", CONFIG_PATH);
        if (!CONFIG_PATH.toFile().exists()) {
            LOGGER.warn("[NeoTowns] Config file not found, using defaults");
            return INSTANCE;
        }
        try (FileConfig config = FileConfig.of(CONFIG_PATH)) {
            config.load();
            INSTANCE.readConfig(config);
        } catch (Exception e) {
            LOGGER.error("[NeoTowns] Failed to load config", e);
        }
        return INSTANCE;
    }

    public void reload() {
        load();
    }

    @SuppressWarnings("unchecked")
    private void readConfig(FileConfig config) {
        townFoundingCost = config.getOrElse("economy.town_founding_cost", townFoundingCost);
        chunkClaimCost = config.getOrElse("economy.chunk_claim_cost", chunkClaimCost);
        outpostClaimMultiplier = config.getOrElse("economy.outpost_claim_multiplier", outpostClaimMultiplier);
        townUpkeepPerChunk = config.getOrElse("economy.town_upkeep_per_chunk", townUpkeepPerChunk);
        stateFoundingCost = config.getOrElse("economy.state_founding_cost", stateFoundingCost);
        stateUpkeepPerTown = config.getOrElse("economy.state_upkeep_per_town", stateUpkeepPerTown);
        nationFoundingCost = config.getOrElse("economy.nation_founding_cost", nationFoundingCost);
        nationUpkeepPerState = config.getOrElse("economy.nation_upkeep_per_state", nationUpkeepPerState);
        startingGrantEmeralds = config.getOrElse("economy.starting_grant_emeralds", startingGrantEmeralds);
        baseClaims = config.getOrElse("towns.base_claims", baseClaims);
        claimsPerResident = config.getOrElse("towns.claims_per_resident", claimsPerResident);
        maxClaimsHardCap = config.getOrElse("towns.max_claims_hard_cap", maxClaimsHardCap);
        debtGraceDays = config.getOrElse("towns.debt_grace_days", debtGraceDays);
        upkeepUseMinecraftDays = config.getOrElse("towns.upkeep_use_minecraft_days", upkeepUseMinecraftDays);
        upkeepMinecraftDayInterval = config.getOrElse("towns.upkeep_minecraft_day_interval", upkeepMinecraftDayInterval);
        upkeepRealTimeHours = config.getOrElse("towns.upkeep_real_time_hours", upkeepRealTimeHours);
        allowUnaffiliatedTowns = config.getOrElse("towns.allow_unaffiliated_towns", allowUnaffiliatedTowns);
        minTownsForState = config.getOrElse("states.min_towns", minTownsForState);
        invitationWindowHours = config.getOrElse("states.invitation_window_hours", invitationWindowHours);
        minStatesForNation = config.getOrElse("nations.min_states", minStatesForNation);
        warMinDurationHours = config.getOrElse("diplomacy.war_min_duration_hours", warMinDurationHours);
        warWarningPeriodHours = config.getOrElse("diplomacy.war_warning_period_hours", warWarningPeriodHours);
        vassalTributePercentage = config.getOrElse("diplomacy.vassal_tribute_percentage", vassalTributePercentage);
        warDeclarationCost = config.getOrElse("economy.war_declaration_cost", warDeclarationCost);
        warSurrenderPenalty = config.getOrElse("economy.war_surrender_penalty", warSurrenderPenalty);
        truceCost = config.getOrElse("economy.truce_cost", truceCost);
        govtypeChangeCost = config.getOrElse("economy.govtype_change_cost", govtypeChangeCost);
        electionIntervalRealDays = config.getOrElse("elections.election_interval_real_days", electionIntervalRealDays);
        votingWindowHours = config.getOrElse("elections.voting_window_hours", votingWindowHours);
        allowReelection = config.getOrElse("elections.allow_reelection", allowReelection);
        maxConsecutiveTerms = config.getOrElse("elections.max_consecutive_terms", maxConsecutiveTerms);
        noConfidenceThresholdPct = config.getOrElse("elections.no_confidence_threshold_pct", noConfidenceThresholdPct);
        coupThresholdPct = config.getOrElse("elections.coup_threshold_pct", coupThresholdPct);
        wildernessBuildDefault = config.getOrElse("protection.wilderness_build_default", wildernessBuildDefault);
        allowMachinesInTown = config.getOrElse("protection.allow_machines_in_town", allowMachinesInTown);
        watchdogEnabled = config.getOrElse("protection.watchdog_enabled", watchdogEnabled);
        watchdogTickInterval = config.getOrElse("protection.watchdog_tick_interval", watchdogTickInterval);
        watchdogRollback = config.getOrElse("protection.watchdog_rollback", watchdogRollback);
        denyCreateContraptions = config.getOrElse("protection.deny_create_contraptions", denyCreateContraptions);
        denyPistonsAcrossClaims = config.getOrElse("protection.deny_pistons_across_claims", denyPistonsAcrossClaims);
        preferredIntegration = config.getOrElse("protection.preferred_integration", preferredIntegration);
        spawnCooldownSeconds = config.getOrElse("teleport.spawn_cooldown_seconds", spawnCooldownSeconds);
        spawnWarmupSeconds = config.getOrElse("teleport.spawn_warmup_seconds", spawnWarmupSeconds);
        allowSpawnInCombat = config.getOrElse("teleport.allow_spawn_in_combat", allowSpawnInCombat);
        dbType = config.getOrElse("database.type", dbType);
        dbPath = config.getOrElse("database.path", dbPath);
        dbHost = config.getOrElse("database.host", dbHost);
        dbName = config.getOrElse("database.name", dbName);
        dbUser = config.getOrElse("database.user", dbUser);
        dbPass = config.getOrElse("database.password", dbPass);
        dbPoolSize = config.getOrElse("database.pool_size", dbPoolSize);
        updateDebounceMs = config.getOrElse("maps.update_debounce_ms", updateDebounceMs);
        townFillOpacity = config.getOrElse("maps.town_fill_opacity", townFillOpacity);
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public static NeoTownsConfig get() { return INSTANCE; }

    public int getTownFoundingCost() { return townFoundingCost; }
    public int getChunkClaimCost() { return chunkClaimCost; }
    public int getOutpostClaimMultiplier() { return outpostClaimMultiplier; }
    public int getTownUpkeepPerChunk() { return townUpkeepPerChunk; }
    public int getStateFoundingCost() { return stateFoundingCost; }
    public int getStateUpkeepPerTown() { return stateUpkeepPerTown; }
    public int getNationFoundingCost() { return nationFoundingCost; }
    public int getNationUpkeepPerState() { return nationUpkeepPerState; }
    public int getStartingGrantEmeralds() { return startingGrantEmeralds; }
    public int getBaseClaims() { return baseClaims; }
    public int getClaimsPerResident() { return claimsPerResident; }
    public int getMaxClaimsHardCap() { return maxClaimsHardCap; }
    public int getDebtGraceDays() { return debtGraceDays; }
    public boolean isUpkeepUseMinecraftDays() { return upkeepUseMinecraftDays; }
    public int getUpkeepMinecraftDayInterval() { return upkeepMinecraftDayInterval; }
    public int getUpkeepRealTimeHours() { return upkeepRealTimeHours; }
    public boolean isAllowUnaffiliatedTowns() { return allowUnaffiliatedTowns; }
    public int getMinTownsForState() { return minTownsForState; }
    public int getInvitationWindowHours() { return invitationWindowHours; }
    public int getMinStatesForNation() { return minStatesForNation; }
    public int getWarMinDurationHours() { return warMinDurationHours; }
    public int getWarWarningPeriodHours() { return warWarningPeriodHours; }
    public int getVassalTributePercentage() { return vassalTributePercentage; }
    public int getWarDeclarationCost() { return warDeclarationCost; }
    public int getWarSurrenderPenalty() { return warSurrenderPenalty; }
    public int getTruceCost() { return truceCost; }
    public int getGovtypeChangeCost() { return govtypeChangeCost; }
    public int getElectionIntervalRealDays() { return electionIntervalRealDays; }
    public int getVotingWindowHours() { return votingWindowHours; }
    public boolean isAllowReelection() { return allowReelection; }
    public int getMaxConsecutiveTerms() { return maxConsecutiveTerms; }
    public int getNoConfidenceThresholdPct() { return noConfidenceThresholdPct; }
    public int getCoupThresholdPct() { return coupThresholdPct; }
    public boolean isWildernessBuildDefault() { return wildernessBuildDefault; }
    public boolean isAllowMachinesInTown() { return allowMachinesInTown; }
    public boolean isWatchdogEnabled() { return watchdogEnabled; }
    public int getWatchdogTickInterval() { return watchdogTickInterval; }
    public boolean isWatchdogRollback() { return watchdogRollback; }
    public boolean isDenyCreateContraptions() { return denyCreateContraptions; }
    public boolean isDenyPistonsAcrossClaims() { return denyPistonsAcrossClaims; }
    public String getPreferredIntegration() { return preferredIntegration; }
    public int getSpawnCooldownSeconds() { return spawnCooldownSeconds; }
    public int getSpawnWarmupSeconds() { return spawnWarmupSeconds; }
    public boolean isAllowSpawnInCombat() { return allowSpawnInCombat; }
    public String getDbType() { return dbType; }
    public String getDbPath() { return dbPath; }
    public String getDbHost() { return dbHost; }
    public String getDbName() { return dbName; }
    public String getDbUser() { return dbUser; }
    public String getDbPass() { return dbPass; }
    public int getDbPoolSize() { return dbPoolSize; }
    public int getUpdateDebounceMs() { return updateDebounceMs; }
    public double getTownFillOpacity() { return townFillOpacity; }
}
