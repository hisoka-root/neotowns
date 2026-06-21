package net.neotowns.handler;

import com.mojang.logging.LogUtils;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.map.MapIntegrationRegistry;
import net.neotowns.model.TownData;
import net.neotowns.util.Messenger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "neotowns")
public final class PlayerEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerEventHandler() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;

        TownData town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town != null) {
            Messenger.info(player, "You are a resident of §b" + town.name() + "§f.");
            if (town.isMayor(player.getUUID())) {
                Messenger.info(player, "You are the §6Mayor§f. Use §e/town§f to manage your town.");
            }
            // Push waypoints via active map integrations
            MapIntegrationRegistry.getActive().forEach(i -> i.onTownFounded(town));
        }
    }
}
