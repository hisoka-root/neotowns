package net.neotowns.command;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.model.TownData;
import net.neotowns.model.TownDataBuilder;
import net.neotowns.util.Messenger;

import java.util.function.Consumer;

public final class TownCommandHelper {

    private TownCommandHelper() {}

    public static TownData requireTown(ServerPlayer player) {
        TownData town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null) {
            Messenger.error(player, "You are not in a town.");
        }
        return town;
    }

    public static boolean requireMayor(ServerPlayer player, TownData town) {
        if (!town.isMayor(player.getUUID())) {
            Messenger.error(player, "Only the mayor can do that.");
            return false;
        }
        return true;
    }

    public static boolean requireAssistantOrMayor(ServerPlayer player, TownData town) {
        if (!town.isMayor(player.getUUID()) && !town.isAssistant(player.getUUID())) {
            Messenger.error(player, "Only the mayor or an assistant can do that.");
            return false;
        }
        return true;
    }

    public static boolean deductFromInventory(ServerPlayer player, int amount) {
        Inventory inv = player.getInventory();
        int available = 0;
        for (ItemStack stack : inv.items) {
            if (stack.is(Items.EMERALD)) available += stack.getCount();
            if (stack.is(Items.EMERALD_BLOCK)) available += stack.getCount() * 9;
        }
        if (available < amount) return false;

        int remaining = amount;
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.is(Items.EMERALD_BLOCK)) {
                int blockValue = stack.getCount() * 9;
                if (blockValue <= remaining) {
                    remaining -= blockValue;
                    inv.setItem(i, ItemStack.EMPTY);
                } else {
                    int blocksToTake = remaining / 9;
                    stack.shrink(blocksToTake);
                    remaining = remaining % 9;
                }
            }
        }
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return true;
    }

    public static TownData updateTown(TownData original, Consumer<TownDataBuilder> consumer) {
        TownDataBuilder builder = new TownDataBuilder(original);
        consumer.accept(builder);
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        return updated;
    }
}
