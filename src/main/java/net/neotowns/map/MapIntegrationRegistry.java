package net.neotowns.map;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class MapIntegrationRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<MapIntegration> active = new ArrayList<>();

    private MapIntegrationRegistry() {}

    public static void init() {
        tryRegister("journeymap", JourneyMapIntegration::new);
        tryRegister("xaerominimap", XaeroIntegration::new);
        tryRegister("xaeroworldmap", XaeroIntegration::new);
        tryRegister("ftbteams", FTBMapIntegration::new);
        tryRegister("ftbchunks", FTBMapIntegration::new);
    }

    private static void tryRegister(String modId, Supplier<MapIntegration> factory) {
        if (ModList.get().isLoaded(modId)) {
            try {
                MapIntegration integration = factory.get();
                active.add(integration);
                LOGGER.info("[NeoTowns] Map integration loaded: {}", modId);
            } catch (Exception e) {
                LOGGER.warn("[NeoTowns] Failed to load map integration for {}: {}", modId, e.getMessage());
            }
        }
    }

    public static List<MapIntegration> getActive() {
        return List.copyOf(active);
    }

    public static void rebuildAll(net.minecraft.server.MinecraftServer server) {
        active.forEach(i -> i.rebuildAll(server));
    }

    public static void shutdown() {
        active.forEach(MapIntegration::shutdown);
    }
}
