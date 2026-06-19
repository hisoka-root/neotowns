package net.neotowns.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public final class TreasuryManager {

    private TreasuryManager() {}

    private static int containerSize(ChestBlockEntity chest) {
        return chest.getContainerSize();
    }

    private static ItemStack getStack(ChestBlockEntity chest, int slot) {
        return chest.getItem(slot);
    }

    private static void setStack(ChestBlockEntity chest, int slot, ItemStack stack) {
        chest.setItem(slot, stack);
    }

    public static long scanTreasury(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return -1;
        long count = 0;
        int size = containerSize(chest);
        for (int i = 0; i < size; i++) {
            ItemStack stack = getStack(chest, i);
            if (stack.is(Items.EMERALD)) count += stack.getCount();
            if (stack.is(Items.EMERALD_BLOCK)) count += stack.getCount() * 9L;
        }
        return count;
    }

    public static boolean deductFromTreasury(ServerLevel level, BlockPos pos, long amount) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return false;
        if (amount <= 0) return true;

        long available = scanTreasury(level, pos);
        if (available < amount) return false;

        long remaining = amount;
        int size = containerSize(chest);

        for (int i = 0; i < size && remaining > 0; i++) {
            ItemStack stack = getStack(chest, i);
            if (stack.isEmpty()) continue;

            if (stack.is(Items.EMERALD_BLOCK)) {
                long blockValue = stack.getCount() * 9L;
                if (blockValue <= remaining) {
                    remaining -= blockValue;
                    setStack(chest, i, ItemStack.EMPTY);
                } else {
                    int blocksToTake = (int) (remaining / 9);
                    int emeraldRemainder = (int) (remaining % 9);
                    stack.shrink(blocksToTake);
                    remaining = emeraldRemainder;
                    if (remaining > 0) {
                        for (int j = 0; j < size && remaining > 0; j++) {
                            ItemStack inner = getStack(chest, j);
                            if (inner.is(Items.EMERALD)) {
                                long take = Math.min(remaining, inner.getCount());
                                inner.shrink((int) take);
                                remaining -= take;
                            }
                        }
                    }
                }
            } else if (stack.is(Items.EMERALD)) {
                long take = Math.min(remaining, stack.getCount());
                stack.shrink((int) take);
                remaining -= take;
            }
        }

        chest.setChanged();
        level.sendBlockUpdated(pos, chest.getBlockState(), chest.getBlockState(), 3);
        return true;
    }

    public static boolean depositToTreasury(ServerLevel level, BlockPos pos, long amount) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return false;
        if (amount <= 0) return true;

        long blocks = amount / 9;
        long singles = amount % 9;
        int size = containerSize(chest);

        for (int i = 0; i < size && blocks > 0; i++) {
            ItemStack stack = getStack(chest, i);
            if (stack.isEmpty()) {
                int toPlace = (int) Math.min(blocks, 64);
                setStack(chest, i, new ItemStack(Items.EMERALD_BLOCK, toPlace));
                blocks -= toPlace;
            } else if (stack.is(Items.EMERALD_BLOCK) && stack.getCount() < 64) {
                int space = 64 - stack.getCount();
                int toAdd = (int) Math.min(blocks, space);
                stack.grow(toAdd);
                blocks -= toAdd;
            }
        }

        for (int i = 0; i < size && singles > 0; i++) {
            ItemStack stack = getStack(chest, i);
            if (stack.isEmpty()) {
                int toPlace = (int) Math.min(singles, 64);
                setStack(chest, i, new ItemStack(Items.EMERALD, toPlace));
                singles -= toPlace;
            } else if (stack.is(Items.EMERALD) && stack.getCount() < 64) {
                int space = 64 - stack.getCount();
                int toAdd = (int) Math.min(singles, space);
                stack.grow(toAdd);
                singles -= toAdd;
            }
        }

        if (blocks > 0 || singles > 0) return false;

        chest.setChanged();
        level.sendBlockUpdated(pos, chest.getBlockState(), chest.getBlockState(), 3);
        return true;
    }

    public static boolean transferBetweenTreasuries(ServerLevel level, BlockPos from, BlockPos to, long amount) {
        if (!deductFromTreasury(level, from, amount)) return false;
        if (!depositToTreasury(level, to, amount)) {
            depositToTreasury(level, from, amount);
            return false;
        }
        return true;
    }
}
