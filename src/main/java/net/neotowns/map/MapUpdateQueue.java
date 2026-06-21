package net.neotowns.map;

import net.neotowns.config.NeoTownsConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = "neotowns")
public final class MapUpdateQueue {

    private static final Map<String, Runnable> pending = new LinkedHashMap<>();
    private static long lastFlushMs = 0;

    private MapUpdateQueue() {}

    public static void enqueue(String key, Runnable update) {
        pending.put(key, update);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        long debounce = NeoTownsConfig.get().getUpdateDebounceMs();
        if (now - lastFlushMs >= debounce && !pending.isEmpty()) {
            pending.values().forEach(Runnable::run);
            pending.clear();
            lastFlushMs = now;
        }
    }
}
