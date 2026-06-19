package net.neotowns.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.event.TownAllyEvent;
import net.neotowns.model.NTId;
import net.neotowns.model.TownData;
import net.neotowns.util.Messenger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = "neotowns")
public final class TownAllyCommand {

    private static final Map<UUID, Set<UUID>> allyCache = new ConcurrentHashMap<>();

    private TownAllyCommand() {}

    public static Set<UUID> getAllies(UUID townId) {
        return allyCache.getOrDefault(townId, Set.of());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        var allyRoot = literal("ally")
            .then(literal("add")
                .then(argument("town", StringArgumentType.word())
                    .executes(TownAllyCommand::executeAllyAdd)))
            .then(literal("remove")
                .then(argument("town", StringArgumentType.word())
                    .executes(TownAllyCommand::executeAllyRemove)))
            .then(literal("list")
                .executes(TownAllyCommand::executeAllyList));

        d.register(literal("town").then(allyRoot));
    }

    private static TownData findTownByName(String name) {
        for (TownData t : NeoTownsCache.allTowns()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    private static int executeAllyAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        String name = StringArgumentType.getString(ctx, "town");
        TownData target = findTownByName(name);
        if (target == null || target.id().equals(town.id())) {
            Messenger.error(player, "No town found with that name.");
            return 1;
        }

        allyCache.computeIfAbsent(town.id().value(), k -> ConcurrentHashMap.newKeySet()).add(target.id().value());
        allyCache.computeIfAbsent(target.id().value(), k -> ConcurrentHashMap.newKeySet()).add(town.id().value());
        DatabaseManager.saveAlly(town.id().value(), target.id().value());

        NeoForge.EVENT_BUS.post(new TownAllyEvent(town, target, true));
        Messenger.success(player, "Added §b" + target.name() + " §fas an ally.");
        return 1;
    }

    private static int executeAllyRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        String name = StringArgumentType.getString(ctx, "town");
        TownData target = findTownByName(name);
        if (target == null) {
            Messenger.error(player, "No town found with that name.");
            return 1;
        }

        var allies = allyCache.get(town.id().value());
        if (allies != null) allies.remove(target.id().value());
        var targetAllies = allyCache.get(target.id().value());
        if (targetAllies != null) targetAllies.remove(town.id().value());
        DatabaseManager.removeAlly(town.id().value(), target.id().value());

        NeoForge.EVENT_BUS.post(new TownAllyEvent(town, target, false));
        Messenger.success(player, "Removed §b" + target.name() + " §ffrom allies.");
        return 1;
    }

    private static int executeAllyList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null) return 1;

        var allies = allyCache.get(town.id().value());
        if (allies == null || allies.isEmpty()) {
            Messenger.info(player, "Your town has no allies.");
            return 1;
        }
        player.sendSystemMessage(Component.literal("§6── Allies of " + town.name() + " ──"));
        for (UUID allyId : allies) {
            String name = NeoTownsCache.getTownName(allyId);
            player.sendSystemMessage(Component.literal(" §e- §f" + name));
        }
        return 1;
    }
}
