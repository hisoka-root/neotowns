package net.neotowns.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.model.NTId;
import net.neotowns.model.TownData;
import net.neotowns.model.TownPerms;
import net.neotowns.model.enums.PermFlag;
import net.neotowns.model.enums.TaxType;
import net.neotowns.util.Messenger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = "neotowns")
public final class TownSetCommand {

    private TownSetCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        var setRoot = literal("set")
            .then(literal("name")
                .then(argument("name", StringArgumentType.word())
                    .executes(TownSetCommand::executeSetName)))
            .then(literal("mayor")
                .then(argument("player", EntityArgument.player())
                    .executes(TownSetCommand::executeSetMayor)))
            .then(literal("role")
                .then(argument("player", EntityArgument.player())
                    .then(argument("role", StringArgumentType.word())
                        .executes(TownSetCommand::executeSetRole))))
            .then(literal("tax")
                .then(argument("amount", IntegerArgumentType.integer(0))
                    .executes(TownSetCommand::executeSetTax)))
            .then(literal("taxtype")
                .then(argument("type", StringArgumentType.word())
                    .executes(TownSetCommand::executeSetTaxType)))
            .then(literal("motd")
                .then(argument("text", StringArgumentType.greedyString())
                    .executes(TownSetCommand::executeSetMotd)))
            .then(literal("pvp")
                .then(argument("value", BoolArgumentType.bool())
                    .executes(TownSetCommand::executeSetPvp)))
            .then(literal("fire")
                .then(argument("value", BoolArgumentType.bool())
                    .executes(TownSetCommand::executeSetFire)))
            .then(literal("mobspawn")
                .then(argument("value", BoolArgumentType.bool())
                    .executes(TownSetCommand::executeSetMobSpawn)))
            .then(literal("open")
                .then(argument("value", BoolArgumentType.bool())
                    .executes(TownSetCommand::executeSetOpen)))
            .then(literal("leadertitle")
                .then(argument("title", StringArgumentType.word())
                    .executes(TownSetCommand::executeSetLeaderTitle)))
            .then(literal("perm")
                .then(argument("group", StringArgumentType.word())
                    .then(argument("flag", StringArgumentType.word())
                        .then(argument("value", BoolArgumentType.bool())
                            .executes(TownSetCommand::executeSetPerm)))));

        d.register(literal("town").then(setRoot));
    }

    private static int executeSetName(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        String name = StringArgumentType.getString(ctx, "name");
        TownData updated = TownCommandHelper.updateTown(town, b -> b.name = name);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Town renamed to §b" + name + "§f.");
        return 1;
    }

    private static int executeSetMayor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        var residents = new HashSet<>(town.residentUUIDs());
        residents.add(target.getUUID());
        TownData updated = TownCommandHelper.updateTown(town, b -> {
            b.mayorUUID = target.getUUID();
            b.residentUUIDs = Collections.unmodifiableSet(residents);
        });
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Transferred mayorhood to §b" + target.getName().getString() + "§f.");
        Messenger.info(target, "You are now the mayor of §b" + town.name() + "§f.");
        return 1;
    }

    private static int executeSetRole(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String role = StringArgumentType.getString(ctx, "role").toUpperCase();
        var assistants = new HashSet<>(town.assistantUUIDs());
        if ("ASSISTANT".equals(role)) {
            assistants.add(target.getUUID());
        } else {
            assistants.remove(target.getUUID());
        }
        TownData updated = TownCommandHelper.updateTown(town, b -> b.assistantUUIDs = Collections.unmodifiableSet(assistants));
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Set §b" + target.getName().getString() + " §fto role §a" + role + "§f.");
        return 1;
    }

    private static int executeSetTax(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        TownData updated = TownCommandHelper.updateTown(town, b -> b.residentTaxEmeralds = amount);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Resident tax set to §e" + amount + " ✦§f.");
        return 1;
    }

    private static int executeSetTaxType(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        String typeName = StringArgumentType.getString(ctx, "type").toUpperCase();
        TaxType type;
        try { type = TaxType.valueOf(typeName); }
        catch (IllegalArgumentException e) {
            Messenger.error(player, "Invalid tax type. Use FLAT, PERCENTAGE, or PER_CHUNK.");
            return 1;
        }
        TownData updated = TownCommandHelper.updateTown(town, b -> b.taxType = type);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Tax type set to §a" + type + "§f.");
        return 1;
    }

    private static int executeSetMotd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;
        String motd = StringArgumentType.getString(ctx, "text");
        TownData updated = TownCommandHelper.updateTown(town, b -> b.motd = motd);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Town MOTD updated.");
        return 1;
    }

    private static int executeSetPvp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return toggleBool(player, ctx, "value", (town, val) -> {
            TownData updated = TownCommandHelper.updateTown(town, b -> b.isPvpEnabled = val);
            DatabaseManager.saveTown(updated);
            Messenger.success(player, "PvP " + (val ? "enabled" : "disabled") + ".");
        });
    }

    private static int executeSetFire(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return toggleBool(player, ctx, "value", (town, val) -> {
            TownData updated = TownCommandHelper.updateTown(town, b -> b.isFireSpread = val);
            DatabaseManager.saveTown(updated);
            Messenger.success(player, "Fire spread " + (val ? "enabled" : "disabled") + ".");
        });
    }

    private static int executeSetMobSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return toggleBool(player, ctx, "value", (town, val) -> {
            TownData updated = TownCommandHelper.updateTown(town, b -> b.isMobSpawn = val);
            DatabaseManager.saveTown(updated);
            Messenger.success(player, "Mob spawning " + (val ? "enabled" : "disabled") + ".");
        });
    }

    private static int executeSetOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return toggleBool(player, ctx, "value", (town, val) -> {
            TownData updated = TownCommandHelper.updateTown(town, b -> b.isOpen = val);
            DatabaseManager.saveTown(updated);
            Messenger.success(player, "Town is now " + (val ? "open" : "closed") + ".");
        });
    }

    @FunctionalInterface
    private interface BoolSetter {
        void accept(TownData town, boolean value);
    }

    private static int toggleBool(ServerPlayer player, CommandContext<CommandSourceStack> ctx, String argName, BoolSetter setter) {
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        boolean value = BoolArgumentType.getBool(ctx, argName);
        setter.accept(town, value);
        return 1;
    }

    private static int executeSetLeaderTitle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        Messenger.info(player, "Leader title updated (display not yet implemented).");
        return 1;
    }

    private static int executeSetPerm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;
        String flagName = StringArgumentType.getString(ctx, "flag").toUpperCase();
        boolean value = BoolArgumentType.getBool(ctx, "value");
        try {
            PermFlag flag = PermFlag.valueOf(flagName);
            TownPerms newPerms = town.perms().with(flag, value);
            TownData updated = TownCommandHelper.updateTown(town, b -> b.perms = newPerms);
            DatabaseManager.saveTown(updated);
            Messenger.success(player, "Permission §a" + flag + " §fset to §a" + (value ? "ON" : "OFF") + "§f.");
        } catch (IllegalArgumentException e) {
            Messenger.error(player, "Unknown permission flag. Use: BUILD, DESTROY, INTERACT, ITEM_USE, SWITCH, PVP, MOB_SPAWN, FIRE, EXPLOSION");
        }
        return 1;
    }
}
