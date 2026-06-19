package net.neotowns.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neotowns.data.ChunkOwnershipCache;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.engine.TreasuryManager;
import net.neotowns.model.PlotData;
import net.neotowns.model.TownData;
import net.neotowns.model.enums.PlotType;
import net.neotowns.util.Messenger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashMap;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = "neotowns")
public final class TownPlotCommand {

    private TownPlotCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        var plotRoot = literal("plot")
            .then(literal("set")
                .then(literal("type")
                    .then(argument("type", StringArgumentType.word())
                        .executes(TownPlotCommand::executePlotSetType))))
            .then(literal("forsale")
                .then(argument("price", IntegerArgumentType.integer(1))
                    .executes(TownPlotCommand::executePlotForSale)))
            .then(literal("notforsale")
                .executes(TownPlotCommand::executePlotNotForSale))
            .then(literal("buy")
                .executes(TownPlotCommand::executePlotBuy))
            .then(literal("evict")
                .executes(TownPlotCommand::executePlotEvict));

        d.register(literal("town").then(plotRoot));
    }

    private static String chunkKey(ChunkPos pos) {
        return pos.toString();
    }

    private static int executePlotSetType(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ChunkPos chunk = new ChunkPos(player.blockPosition());
        UUID owner = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
        if (owner == null || !owner.equals(town.id().value())) {
            Messenger.error(player, "This chunk is not claimed by your town.");
            return 1;
        }

        String typeName = StringArgumentType.getString(ctx, "type").toUpperCase();
        PlotType type;
        try { type = PlotType.valueOf(typeName); }
        catch (IllegalArgumentException e) {
            Messenger.error(player, "Invalid plot type. Use: DEFAULT, SHOP, FARM, ARENA, EMBASSY, OUTPOST, QUARRY, SACRED");
            return 1;
        }

        var plots = new HashMap<>(town.plots());
        var existing = plots.get(chunkKey(chunk));
        if (existing != null) {
            plots.put(chunkKey(chunk), new PlotData(chunk, player.serverLevel().dimension(), town.id(), type, existing.ownerUUID(), existing.salePrice(), existing.isEmbassy()));
        }
        TownData updated = TownCommandHelper.updateTown(town, b -> b.plots = plots);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Plot type set to §a" + type + "§f.");
        return 1;
    }

    private static int executePlotForSale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ChunkPos chunk = new ChunkPos(player.blockPosition());
        UUID owner = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
        if (owner == null || !owner.equals(town.id().value())) {
            Messenger.error(player, "This chunk is not claimed by your town.");
            return 1;
        }

        int price = IntegerArgumentType.getInteger(ctx, "price");
        var plots = new HashMap<>(town.plots());
        var existing = plots.get(chunkKey(chunk));
        plots.put(chunkKey(chunk), new PlotData(chunk, player.serverLevel().dimension(), town.id(),
            existing != null ? existing.type() : PlotType.DEFAULT, existing != null ? existing.ownerUUID() : null, price, false));
        TownData updated = TownCommandHelper.updateTown(town, b -> b.plots = plots);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Plot listed for §e" + price + " ✦§f.");
        return 1;
    }

    private static int executePlotNotForSale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ChunkPos chunk = new ChunkPos(player.blockPosition());
        var plots = new HashMap<>(town.plots());
        var existing = plots.get(chunkKey(chunk));
        if (existing != null) {
            plots.put(chunkKey(chunk), new PlotData(chunk, player.serverLevel().dimension(), town.id(),
                existing.type(), existing.ownerUUID(), 0, existing.isEmbassy()));
        }
        TownData updated = TownCommandHelper.updateTown(town, b -> b.plots = plots);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Plot removed from market.");
        return 1;
    }

    private static int executePlotBuy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null) return 1;

        ChunkPos chunk = new ChunkPos(player.blockPosition());
        UUID chunkOwner = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
        if (chunkOwner == null || !chunkOwner.equals(town.id().value())) {
            Messenger.error(player, "This chunk is not claimed by your town.");
            return 1;
        }

        var existing = town.plots().get(chunkKey(chunk));
        if (existing == null || existing.salePrice() <= 0) {
            Messenger.error(player, "This plot is not for sale.");
            return 1;
        }

        int price = (int) existing.salePrice();
        if (!TownCommandHelper.deductFromInventory(player, price)) {
            Messenger.error(player, "You need " + price + " emeralds.");
            return 1;
        }

        TreasuryManager.depositToTreasury(player.serverLevel(), town.treasuryChestPos(), price);

        var plots = new HashMap<>(town.plots());
        plots.put(chunkKey(chunk), new PlotData(chunk, player.serverLevel().dimension(), town.id(),
            existing.type(), player.getUUID(), 0, false));
        TownData updated = TownCommandHelper.updateTown(town, b -> b.plots = plots);
        NeoTownsCache.putTown(updated);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "You bought this plot for §e" + price + " ✦§f.");
        return 1;
    }

    private static int executePlotEvict(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ChunkPos chunk = new ChunkPos(player.blockPosition());
        var existing = town.plots().get(chunkKey(chunk));
        if (existing == null || existing.ownerUUID() == null) {
            Messenger.error(player, "This plot has no owner to evict.");
            return 1;
        }

        var plots = new HashMap<>(town.plots());
        plots.put(chunkKey(chunk), new PlotData(chunk, player.serverLevel().dimension(), town.id(),
            existing.type(), null, 0, false));
        TownData updated = TownCommandHelper.updateTown(town, b -> b.plots = plots);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Evicted resident from this plot.");
        return 1;
    }
}
