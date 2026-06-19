package net.neotowns.handler;

import com.mojang.logging.LogUtils;
import net.neotowns.data.DatabaseManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = "neotowns")
public final class ServerLifecycleHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerLifecycleHandler() {}

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[NeoTowns] Server stopping — shutting down database...");
        DatabaseManager.shutdown();
    }
}
