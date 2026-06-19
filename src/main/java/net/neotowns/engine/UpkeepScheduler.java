package net.neotowns.engine;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.event.TownDebtEvent;
import net.neotowns.event.TownDisbandEvent;
import net.neotowns.event.TownTaxEvent;
import net.neotowns.model.TownData;
import net.neotowns.util.Messenger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "neotowns")
public final class UpkeepScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TICKS_PER_MINECRAFT_DAY = 24000L;
    private static int tickCounter = 0;

    private UpkeepScheduler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        var overworld = server.overworld();
        if (overworld == null) return;

        int interval = NeoTownsConfig.get().isUpkeepUseMinecraftDays()
            ? (int) (TICKS_PER_MINECRAFT_DAY * NeoTownsConfig.get().getUpkeepMinecraftDayInterval())
            : (int) (20L * 60 * 60 * NeoTownsConfig.get().getUpkeepRealTimeHours());

        tickCounter++;
        if (tickCounter < interval) return;
        tickCounter = 0;

        runUpkeep(server);
    }

    private static void runUpkeep(MinecraftServer server) {
        var overworld = server.overworld();
        if (overworld == null) return;

        for (TownData town : NeoTownsCache.allTowns()) {
            var level = server.getLevel(town.treasuryWorld());
            if (level == null) continue;

            long cost = (long) town.dailyUpkeepPerChunk() * town.claimCount();
            var taxEvent = new TownTaxEvent(town, cost);
            NeoForge.EVENT_BUS.post(taxEvent);

            if (taxEvent.getTaxAmount() <= 0) continue;

            boolean paid = TreasuryManager.deductFromTreasury(level,
                town.treasuryChestPos(), taxEvent.getTaxAmount());

            if (paid) {
                clearDebt(town.id().value());
            } else {
                int daysInDebt = incrementDebt(town.id().value());
                NeoForge.EVENT_BUS.post(new TownDebtEvent(town, daysInDebt));

                Messenger.broadcast(server.getPlayerList().getPlayers(),
                    "§b" + town.name() + " §eis in debt (§e" + daysInDebt + "§e/§e"
                    + NeoTownsConfig.get().getDebtGraceDays() + "§e days§e).");

                if (daysInDebt >= NeoTownsConfig.get().getDebtGraceDays()) {
                    autoDisband(town, server);
                }
            }
        }
    }

    private static final Map<UUID, Integer> debtDays = new ConcurrentHashMap<>();
    private static void clearDebt(UUID townId) { debtDays.remove(townId); }
    private static int incrementDebt(UUID townId) {
        int days = debtDays.merge(townId, 1, Integer::sum);
        return days;
    }

    private static void autoDisband(TownData town, MinecraftServer server) {
        LOGGER.info("[NeoTowns] Auto-disbanding town {} due to unpaid debt", town.name());
        DatabaseManager.deleteTown(town.id().value());
        NeoTownsCache.removeTown(town.id().value());
        NeoForge.EVENT_BUS.post(new TownDisbandEvent(town, null));
        Messenger.broadcast(server.getPlayerList().getPlayers(),
            "§b" + town.name() + " §chas been disbanded due to unpaid debt.");
    }
}
