package net.neotowns.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Messenger {

    private Messenger() {}

    public static void info(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§a[NeoTowns] §f" + message));
    }

    public static void success(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§a[NeoTowns] §a✔ §f" + message));
    }

    public static void warn(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§e[NeoTowns] §e⚠ §f" + message));
    }

    public static void error(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§c[NeoTowns] §c✘ §f" + message));
    }

    public static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§c[NeoTowns] §c⛔ §f" + message));
    }

    public static void broadcast(Iterable<ServerPlayer> players, String message) {
        var component = Component.literal("§6[NeoTowns] §f" + message);
        for (ServerPlayer p : players) {
            p.sendSystemMessage(component);
        }
    }
}
