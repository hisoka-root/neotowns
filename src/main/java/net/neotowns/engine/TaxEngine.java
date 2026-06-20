package net.neotowns.engine;

import net.minecraft.server.level.ServerLevel;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.event.StateTaxEvent;
import net.neotowns.model.StateData;
import net.neotowns.model.TownData;
import net.neotowns.model.enums.TaxType;
import net.neotowns.NeoTownsMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "neotowns")
public final class TaxEngine {

    private static int tickCounter = 0;

    private TaxEngine() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        int interval = NeoTownsConfig.get().isUpkeepUseMinecraftDays()
            ? (int) (24000L * NeoTownsConfig.get().getUpkeepMinecraftDayInterval())
            : (int) (20L * 60 * 60 * NeoTownsConfig.get().getUpkeepRealTimeHours());
        if (tickCounter < interval) return;
        tickCounter = 0;

        collectStateTaxes(event.getServer());
    }

    private static void collectStateTaxes(net.minecraft.server.MinecraftServer server) {
        for (StateData state : NeoTownsCache.allStates()) {
            if (state.stateTaxEmeralds() <= 0) continue;

            var level = server.getLevel(state.treasuryWorld());
            if (!(level instanceof ServerLevel sl)) continue;

            long totalCollected = 0;

            for (var townId : state.townIds()) {
                TownData town = NeoTownsCache.getTown(townId.value());
                if (town == null) continue;

                var townLevel = server.getLevel(town.treasuryWorld());
                if (!(townLevel instanceof ServerLevel tl)) continue;

                long taxAmount = switch (state.stateTaxType()) {
                    case FLAT -> state.stateTaxEmeralds();
                    case PER_CHUNK -> state.stateTaxEmeralds() * town.claimCount();
                    case PERCENTAGE -> {
                        long balance = TreasuryManager.scanTreasury(tl, town.treasuryChestPos());
                        if (balance <= 0) yield 0L;
                        yield (long) (balance * (state.stateTaxEmeralds() / 100.0));
                    }
                };

                if (taxAmount <= 0) continue;

                var taxEvent = new StateTaxEvent(state, taxAmount);
                NeoForge.EVENT_BUS.post(taxEvent);

                if (TreasuryManager.deductFromTreasury(tl, town.treasuryChestPos(), taxEvent.getTaxAmount())) {
                    if (TreasuryManager.depositToTreasury(sl, state.treasuryChestPos(), taxEvent.getTaxAmount())) {
                        totalCollected += taxEvent.getTaxAmount();
                    } else {
                        TreasuryManager.depositToTreasury(tl, town.treasuryChestPos(), taxEvent.getTaxAmount());
                    }
                }
            }

            if (totalCollected > 0) {
                NeoTownsMod.LOGGER.info("[NeoTowns] Collected {} emeralds in state tax for {}", totalCollected, state.name());
            }
        }
    }
}
