package net.neotowns.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.engine.TreasuryManager;
import net.neotowns.event.*;
import net.neotowns.model.*;
import net.neotowns.model.enums.GovernmentType;
import net.neotowns.model.enums.TaxType;
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
public final class StateCommand {

    private StateCommand() {}

    // ── Pending state founding ──────────────────────────────────────────────
    private record PendingFounding(String name, GovernmentType govType, UUID founderId, Set<UUID> consentingTowns, long expiresAt) {}
    private static final Map<String, PendingFounding> pendingFoundings = new ConcurrentHashMap<>();

    // ── State sessions ──────────────────────────────────────────────────────
    private record StateSession(String topic, Map<UUID, Boolean> votes, int quorum, long closesAt) {}
    private static final Map<UUID, StateSession> activeSessions = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        var stateRoot = literal("state")
            .executes(StateCommand::executeDashboard)
            .then(literal("help").executes(StateCommand::executeHelp))
            .then(literal("found")
                .then(argument("name", StringArgumentType.word())
                    .then(argument("govtype", StringArgumentType.word())
                        .executes(StateCommand::executeFound))))
            .then(literal("disband").executes(StateCommand::executeDisband))
            .then(literal("invite")
                .then(argument("town", StringArgumentType.word())
                    .executes(StateCommand::executeInvite)))
            .then(literal("join")
                .then(argument("name", StringArgumentType.word())
                    .executes(StateCommand::executeJoin)))
            .then(literal("leave").executes(StateCommand::executeLeave))
            .then(literal("kick")
                .then(argument("town", StringArgumentType.word())
                    .executes(StateCommand::executeKick)))
            .then(literal("set")
                .then(literal("name")
                    .then(argument("value", StringArgumentType.word())
                        .executes(ctx -> executeSet(ctx, "name"))))
                .then(literal("chancellor")
                    .then(argument("player", EntityArgument.player())
                        .executes(ctx -> executeSet(ctx, "chancellor"))))
                .then(literal("leadertitle")
                    .then(argument("value", StringArgumentType.word())
                        .executes(ctx -> executeSet(ctx, "leadertitle"))))
                .then(literal("tax")
                    .then(argument("amount", IntegerArgumentType.integer(0))
                        .executes(ctx -> executeSet(ctx, "tax"))))
                .then(literal("taxtype")
                    .then(argument("value", StringArgumentType.word())
                        .executes(ctx -> executeSet(ctx, "taxtype"))))
                .then(literal("govtype")
                    .then(argument("value", StringArgumentType.word())
                        .executes(ctx -> executeSet(ctx, "govtype"))))
                .then(literal("capital")
                    .then(argument("town", StringArgumentType.word())
                        .executes(ctx -> executeSet(ctx, "capital")))))
            .then(literal("law")
                .then(literal("add")
                    .then(argument("name", StringArgumentType.word())
                        .then(argument("text", StringArgumentType.greedyString())
                            .executes(StateCommand::executeLawAdd))))
                .then(literal("remove")
                    .then(argument("name", StringArgumentType.word())
                        .executes(StateCommand::executeLawRemove)))
                .then(literal("list").executes(StateCommand::executeLawList)))
            .then(literal("constitution")
                .then(literal("set")
                    .then(argument("text", StringArgumentType.greedyString())
                        .executes(StateCommand::executeConstitutionSet)))
                .then(literal("view").executes(StateCommand::executeConstitutionView)))
            .then(literal("cabinet")
                .then(literal("add")
                    .then(argument("player", EntityArgument.player())
                        .then(argument("role", StringArgumentType.word())
                            .executes(StateCommand::executeCabinetAdd))))
                .then(literal("remove")
                    .then(argument("player", EntityArgument.player())
                        .executes(StateCommand::executeCabinetRemove)))
                .then(literal("list").executes(StateCommand::executeCabinetList)))
            .then(literal("session")
                .then(literal("start")
                    .then(argument("topic", StringArgumentType.greedyString())
                        .executes(StateCommand::executeSessionStart)))
                .then(literal("vote")
                    .then(argument("choice", StringArgumentType.word())
                        .executes(StateCommand::executeSessionVote)))
                .then(literal("result").executes(StateCommand::executeSessionResult)))
            .then(literal("deposit")
                .then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(StateCommand::executeDeposit)))
            .then(literal("withdraw")
                .then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(StateCommand::executeWithdraw)))
            .then(literal("balance").executes(StateCommand::executeBalance))
            .then(literal("spawn").executes(StateCommand::executeSpawn))
            .then(literal("list").executes(StateCommand::executeList))
            .then(literal("info")
                .executes(ctx -> executeInfo(ctx, null))
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "name")))));

        d.register(stateRoot);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static StateData requireState(ServerPlayer player) {
        TownData town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null || town.stateId() == null) {
            Messenger.error(player, "You are not in a state.");
            return null;
        }
        StateData state = NeoTownsCache.getState(town.stateId().value());
        if (state == null) {
            Messenger.error(player, "State data not found.");
            return null;
        }
        return state;
    }

    private static boolean requireChancellor(ServerPlayer player, StateData state) {
        if (!state.isChancellor(player.getUUID())) {
            Messenger.error(player, "Only the chancellor can do that.");
            return false;
        }
        return true;
    }

    // ── Dashboard ───────────────────────────────────────────────────────────

    private static int executeDashboard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        String chancellorName = ctx.getSource().getServer().getPlayerList()
            .getPlayer(state.chancellorUUID()) != null
            ? ctx.getSource().getServer().getPlayerList().getPlayer(state.chancellorUUID()).getName().getString()
            : "Unknown";
        player.sendSystemMessage(Component.literal("§5═══ §d" + state.name() + " §5═══"));
        player.sendSystemMessage(Component.literal("§7Chancellor: §f" + chancellorName));
        player.sendSystemMessage(Component.literal("§7Government: §f" + state.governmentType()));
        player.sendSystemMessage(Component.literal("§7Towns: §f" + state.townIds().size()));
        player.sendSystemMessage(Component.literal("§7Laws: §f" + state.laws().size()));
        player.sendSystemMessage(Component.literal("§7Cabinet: §f" + state.cabinet().size() + " members"));
        if (state.hasNation()) {
            String nn = NeoTownsCache.getNationName(state.nationId().value());
            if (nn != null) player.sendSystemMessage(Component.literal("§7Nation: §f" + nn));
        }
        player.sendSystemMessage(Component.literal("§5═══════════════"));
        return 1;
    }

    // ── Help ────────────────────────────────────────────────────────────────

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("§5── State Commands ──"));
        player.sendSystemMessage(Component.literal("§d/state §7— Dashboard"));
        player.sendSystemMessage(Component.literal("§d/state found <name> <govtype>§7— Found a state"));
        player.sendSystemMessage(Component.literal("§d/state join|leave|kick§7— Membership"));
        player.sendSystemMessage(Component.literal("§d/state set <setting> <value>§7— Configure"));
        player.sendSystemMessage(Component.literal("§d/state law <add|remove|list>§7— Laws"));
        player.sendSystemMessage(Component.literal("§d/state constitution set|view§7— Constitution"));
        player.sendSystemMessage(Component.literal("§d/state cabinet <add|remove|list>§7— Cabinet"));
        player.sendSystemMessage(Component.literal("§d/state session <start|vote|result>§7— Sessions"));
        player.sendSystemMessage(Component.literal("§d/state deposit|withdraw|balance§7— Treasury"));
        player.sendSystemMessage(Component.literal("§d/state spawn§7— Teleport to capital"));
        return 1;
    }

    // ── Found ───────────────────────────────────────────────────────────────

    private static int executeFound(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null) {
            Messenger.error(player, "You must be a mayor of a town to found a state.");
            return 1;
        }
        if (!town.isMayor(player.getUUID())) {
            Messenger.error(player, "Only a mayor can found a state.");
            return 1;
        }
        if (town.stateId() != null) {
            Messenger.error(player, "Your town is already in a state.");
            return 1;
        }

        String name = StringArgumentType.getString(ctx, "name");
        String govTypeStr = StringArgumentType.getString(ctx, "govtype").toUpperCase();
        GovernmentType govType;
        try { govType = GovernmentType.valueOf(govTypeStr); }
        catch (IllegalArgumentException e) {
            Messenger.error(player, "Invalid government type. Use: DEMOCRACY, OLIGARCHY, MONARCHY, REPUBLIC, THEOCRACY, COUNCIL");
            return 1;
        }

        for (StateData s : NeoTownsCache.allStates()) {
            if (s.name().equalsIgnoreCase(name)) {
                Messenger.error(player, "A state with that name already exists.");
                return 1;
            }
        }

        int cost = NeoTownsConfig.get().getStateFoundingCost();
        if (!TownCommandHelper.deductFromInventory(player, cost)) {
            Messenger.error(player, "You need " + cost + " emeralds to found a state.");
            return 1;
        }

        // Check for chest near player
        BlockPos chestPos = null;
        var playerPos = player.blockPosition();
        for (int dx = -5; dx <= 5 && chestPos == null; dx++)
            for (int dy = -5; dy <= 5 && chestPos == null; dy++)
                for (int dz = -5; dz <= 5 && chestPos == null; dz++) {
                    var be = player.serverLevel().getBlockEntity(playerPos.offset(dx, dy, dz));
                    if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                        chestPos = playerPos.offset(dx, dy, dz);
                    }
                }
        if (chestPos == null) {
            // refund
            player.addItem(new ItemStack(Items.EMERALD, cost));
            Messenger.error(player, "Place a chest within 5 blocks first.");
            return 1;
        }

        // Create pending founding
        var consenting = new HashSet<UUID>();
        consenting.add(town.id().value());
        long expiresAt = System.currentTimeMillis() + NeoTownsConfig.get().getInvitationWindowHours() * 3600000L;
        pendingFoundings.put(name.toLowerCase(), new PendingFounding(name, govType, player.getUUID(), consenting, expiresAt));
        Messenger.success(player, "State §d" + name + " §fpending founding. Other mayors: §e/state join " + name);
        return 1;
    }

    // ── Join (to a pending founding) ────────────────────────────────────────

    private static int executeJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null || !town.isMayor(player.getUUID())) {
            Messenger.error(player, "Only a mayor can join a pending state founding.");
            return 1;
        }
        if (town.stateId() != null) {
            Messenger.error(player, "Your town is already in a state.");
            return 1;
        }

        String name = StringArgumentType.getString(ctx, "name");
        PendingFounding pf = pendingFoundings.get(name.toLowerCase());
        if (pf == null) {
            Messenger.error(player, "No pending state founding with that name.");
            return 1;
        }
        if (System.currentTimeMillis() > pf.expiresAt()) {
            pendingFoundings.remove(name.toLowerCase());
            Messenger.error(player, "The founding window for §d" + name + " §fhas expired.");
            return 1;
        }
        if (pf.consentingTowns().contains(town.id().value())) {
            Messenger.info(player, "Your town has already consented.");
            return 1;
        }

        pf.consentingTowns().add(town.id().value());
        int minTowns = NeoTownsConfig.get().getMinTownsForState();
        Messenger.success(player, "Your town consents to join §d" + name + "§f. (" + pf.consentingTowns().size() + "/" + minTowns + " mayors)");

        if (pf.consentingTowns().size() >= minTowns) {
            finalizeFounding(pf);
            pendingFoundings.remove(name.toLowerCase());
        }
        return 1;
    }

    private static void finalizeFounding(PendingFounding pf) {
        NTId stateId = NTId.random();
        long now = System.currentTimeMillis() / 86400000L;
        var builder = new StateDataBuilder();
        builder.id = stateId;
        builder.name = pf.name();
        builder.chancellorUUID = pf.founderId();
        builder.townIds = new HashSet<>();
        builder.nationId = null;
        builder.treasuryChestPos = null;
        builder.treasuryWorld = null;
        builder.stateTaxEmeralds = 0;
        builder.stateTaxType = TaxType.FLAT;
        builder.governmentType = pf.govType();
        builder.laws = new HashMap<>();
        builder.constitution = "No constitution has been written.";
        builder.cabinet = new HashMap<>();
        builder.foundedEpochDay = now;

        // Set treasury from the first town
        for (UUID townId : pf.consentingTowns()) {
            TownData town = NeoTownsCache.getTown(townId);
            if (town != null && builder.treasuryChestPos == null) {
                builder.treasuryWorld = town.treasuryWorld();
                builder.treasuryChestPos = town.treasuryChestPos();
            }
        }

        StateData state = builder.build();

        // Update all consenting towns
        for (UUID townId : pf.consentingTowns()) {
            TownData town = NeoTownsCache.getTown(townId);
            if (town == null) continue;
            builder.townIds.add(town.id());

            var updated = TownCommandHelper.updateTown(town, b -> b.stateId = state.id());
            DatabaseManager.saveTown(updated);
        }

        NeoTownsCache.putState(state);
        DatabaseManager.saveState(state);
        NeoForge.EVENT_BUS.post(new StateFoundEvent(state));
    }

    // ── Invite ──────────────────────────────────────────────────────────────

    private static int executeInvite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        String townName = StringArgumentType.getString(ctx, "town");
        TownData target = null;
        for (TownData t : NeoTownsCache.allTowns()) {
            if (t.name().equalsIgnoreCase(townName)) { target = t; break; }
        }
        if (target == null) {
            Messenger.error(player, "No town found with that name.");
            return 1;
        }
        if (target.stateId() != null) {
            Messenger.error(player, "That town is already in a state.");
            return 1;
        }

        // Store pending invite in town command's invite system — reuse TownCommand's mechanism
        // For simplicity, just notify the mayors directly
        var mayor = ctx.getSource().getServer().getPlayerList().getPlayer(target.mayorUUID());
        if (mayor != null) {
            Messenger.info(mayor, "You are invited to join §d" + state.name() + "§f. Use §e/state join " + state.name() + "§f.");
        }
        Messenger.success(player, "Invited §b" + target.name() + " §fto join the state.");
        return 1;
    }

    // ── Leave ───────────────────────────────────────────────────────────────

    private static int executeLeave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null || !town.isMayor(player.getUUID())) {
            Messenger.error(player, "Only a mayor can leave a state.");
            return 1;
        }
        if (town.stateId() == null) {
            Messenger.error(player, "Your town is not in a state.");
            return 1;
        }

        StateData state = NeoTownsCache.getState(town.stateId().value());
        if (state == null) return 1;

        var updatedTowns = new HashSet<>(state.townIds());
        updatedTowns.remove(town.id());
        var builder = new StateDataBuilder(state);
        builder.townIds = Collections.unmodifiableSet(updatedTowns);
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);

        var townUpdated = TownCommandHelper.updateTown(town, b -> b.stateId = null);
        DatabaseManager.saveTown(townUpdated);
        DatabaseManager.saveState(updated);

        NeoForge.EVENT_BUS.post(new StateMembershipEvent(updated, townUpdated, false));

        if (updatedTowns.isEmpty()) {
            NeoTownsCache.removeState(state.id().value());
            DatabaseManager.deleteState(state.id().value());
            NeoForge.EVENT_BUS.post(new StateDisbandEvent(updated));
            Messenger.info(player, "The state has disbanded as no towns remain.");
        } else {
            Messenger.success(player, "Your town left §d" + state.name() + "§f.");
        }
        return 1;
    }

    // ── Kick ────────────────────────────────────────────────────────────────

    private static int executeKick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        String townName = StringArgumentType.getString(ctx, "town");
        TownData target = null;
        for (TownData t : NeoTownsCache.allTowns()) {
            if (t.name().equalsIgnoreCase(townName) && state.townIds().contains(t.id())) {
                target = t; break;
            }
        }
        if (target == null) {
            Messenger.error(player, "No member town found with that name.");
            return 1;
        }

        var updatedTowns = new HashSet<>(state.townIds());
        updatedTowns.remove(target.id());
        var builder = new StateDataBuilder(state);
        builder.townIds = Collections.unmodifiableSet(updatedTowns);
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);

        var townUpdated = TownCommandHelper.updateTown(target, b -> b.stateId = null);
        DatabaseManager.saveTown(townUpdated);
        DatabaseManager.saveState(updated);
        NeoForge.EVENT_BUS.post(new StateMembershipEvent(updated, townUpdated, false));
        Messenger.success(player, "Kicked §b" + target.name() + " §ffrom the state.");

        var mayor = ctx.getSource().getServer().getPlayerList().getPlayer(target.mayorUUID());
        if (mayor != null) Messenger.warn(mayor, "Your town was kicked from §d" + state.name() + "§f.");
        return 1;
    }

    // ── Disband ─────────────────────────────────────────────────────────────

    private static int executeDisband(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        for (var townId : state.townIds()) {
            TownData town = NeoTownsCache.getTown(townId.value());
            if (town == null) continue;
            var updated = TownCommandHelper.updateTown(town, b -> b.stateId = null);
            DatabaseManager.saveTown(updated);
        }
        NeoTownsCache.removeState(state.id().value());
        DatabaseManager.deleteState(state.id().value());
        NeoForge.EVENT_BUS.post(new StateDisbandEvent(state));
        Messenger.success(player, "State §d" + state.name() + " §fdisbanded.");
        return 1;
    }

    // ── Set ─────────────────────────────────────────────────────────────────

    private static int executeSet(CommandContext<CommandSourceStack> ctx, String field) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        var builder = new StateDataBuilder(state);
        switch (field) {
            case "name" -> {
                String name = StringArgumentType.getString(ctx, "value");
                builder.name = name;
                Messenger.success(player, "State renamed to §d" + name + "§f.");
            }
            case "chancellor" -> {
                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                builder.chancellorUUID = target.getUUID();
                Messenger.success(player, "Chancellorship transferred to §b" + target.getName().getString() + "§f.");
                Messenger.info(target, "You are now the chancellor of §d" + state.name() + "§f.");
            }
            case "leadertitle" -> {
                Messenger.info(player, "Leader title updated (display not yet implemented).");
            }
            case "tax" -> {
                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                builder.stateTaxEmeralds = amount;
                Messenger.success(player, "State tax set to §e" + amount + " ✦§f.");
            }
            case "taxtype" -> {
                String val = StringArgumentType.getString(ctx, "value").toUpperCase();
                try { builder.stateTaxType = TaxType.valueOf(val); }
                catch (IllegalArgumentException e) {
                    Messenger.error(player, "Invalid tax type. Use FLAT, PERCENTAGE, PER_CHUNK.");
                    return 1;
                }
                Messenger.success(player, "Tax type set to §a" + builder.stateTaxType + "§f.");
            }
            case "govtype" -> {
                String val = StringArgumentType.getString(ctx, "value").toUpperCase();
                try { builder.governmentType = GovernmentType.valueOf(val); }
                catch (IllegalArgumentException e) {
                    Messenger.error(player, "Invalid government type.");
                    return 1;
                }
                Messenger.success(player, "Government type changed to §a" + builder.governmentType + "§f.");
            }
            case "capital" -> {
                Messenger.info(player, "Capital designation not yet implemented.");
            }
        }
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);
        DatabaseManager.saveState(updated);
        return 1;
    }

    // ── Law ─────────────────────────────────────────────────────────────────

    private static int executeLawAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        String lawName = StringArgumentType.getString(ctx, "name");
        String lawText = StringArgumentType.getString(ctx, "text");

        var updatedLaws = new HashMap<>(state.laws());
        updatedLaws.put(lawName, lawText);
        var builder = new StateDataBuilder(state);
        builder.laws = Collections.unmodifiableMap(updatedLaws);
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);
        DatabaseManager.saveState(updated);
        NeoForge.EVENT_BUS.post(new LawEnactedEvent(updated, lawName, lawText));
        Messenger.success(player, "Law §a" + lawName + " §fenacted.");
        return 1;
    }

    private static int executeLawRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        String lawName = StringArgumentType.getString(ctx, "name");
        var updatedLaws = new HashMap<>(state.laws());
        updatedLaws.remove(lawName);
        var builder = new StateDataBuilder(state);
        builder.laws = Collections.unmodifiableMap(updatedLaws);
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);
        DatabaseManager.saveState(updated);
        NeoForge.EVENT_BUS.post(new LawRepealedEvent(updated, lawName));
        Messenger.success(player, "Law §a" + lawName + " §frepealed.");
        return 1;
    }

    private static int executeLawList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        if (state.laws().isEmpty()) {
            Messenger.info(player, "No laws enacted.");
            return 1;
        }
        player.sendSystemMessage(Component.literal("§5── Laws of " + state.name() + " ──"));
        for (var entry : state.laws().entrySet()) {
            player.sendSystemMessage(Component.literal(" §d" + entry.getKey() + "§7: §f" + entry.getValue()));
        }
        return 1;
    }

    // ── Constitution ────────────────────────────────────────────────────────

    private static int executeConstitutionSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        String text = StringArgumentType.getString(ctx, "text");
        var builder = new StateDataBuilder(state);
        builder.constitution = text;
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);
        DatabaseManager.saveState(updated);
        Messenger.success(player, "Constitution updated.");
        return 1;
    }

    private static int executeConstitutionView(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        String text = state.constitution() != null ? state.constitution() : "No constitution has been written.";
        player.sendSystemMessage(Component.literal("§5── Constitution of " + state.name() + " ──"));
        player.sendSystemMessage(Component.literal("§f" + text));
        return 1;
    }

    // ── Cabinet ─────────────────────────────────────────────────────────────

    private static int executeCabinetAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String role = StringArgumentType.getString(ctx, "role");

        var updatedCabinet = new HashMap<>(state.cabinet());
        updatedCabinet.put(target.getUUID(), role);
        var builder = new StateDataBuilder(state);
        builder.cabinet = Collections.unmodifiableMap(updatedCabinet);
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);
        DatabaseManager.saveState(updated);
        Messenger.success(player, "Appointed §b" + target.getName().getString() + " §fas §a" + role + "§f.");
        return 1;
    }

    private static int executeCabinetRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        var updatedCabinet = new HashMap<>(state.cabinet());
        updatedCabinet.remove(target.getUUID());
        var builder = new StateDataBuilder(state);
        builder.cabinet = Collections.unmodifiableMap(updatedCabinet);
        StateData updated = builder.build();
        NeoTownsCache.putState(updated);
        DatabaseManager.saveState(updated);
        Messenger.success(player, "Removed §b" + target.getName().getString() + " §ffrom cabinet.");
        return 1;
    }

    private static int executeCabinetList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        if (state.cabinet().isEmpty()) {
            Messenger.info(player, "No cabinet members appointed.");
            return 1;
        }
        player.sendSystemMessage(Component.literal("§5── Cabinet of " + state.name() + " ──"));
        for (var entry : state.cabinet().entrySet()) {
            String name = ctx.getSource().getServer().getPlayerList().getPlayer(entry.getKey()) != null
                ? ctx.getSource().getServer().getPlayerList().getPlayer(entry.getKey()).getName().getString()
                : entry.getKey().toString().substring(0, 8);
            player.sendSystemMessage(Component.literal(" §d" + entry.getValue() + "§7: §f" + name));
        }
        return 1;
    }

    // ── Session ─────────────────────────────────────────────────────────────

    private static int executeSessionStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        if (activeSessions.containsKey(state.id().value())) {
            Messenger.error(player, "A session is already active.");
            return 1;
        }

        String topic = StringArgumentType.getString(ctx, "topic");
        long duration = NeoTownsConfig.get().getVotingWindowHours() * 3600000L;
        int quorum = Math.max(1, state.townIds().size() / 2);
        activeSessions.put(state.id().value(), new StateSession(topic, new ConcurrentHashMap<>(), quorum, System.currentTimeMillis() + duration));
        Messenger.success(player, "Session started: §e" + topic + "§f. Voting ends in " + NeoTownsConfig.get().getVotingWindowHours() + " hours.");
        return 1;
    }

    private static int executeSessionVote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        StateSession session = activeSessions.get(state.id().value());
        if (session == null) {
            Messenger.error(player, "No active session.");
            return 1;
        }
        if (System.currentTimeMillis() > session.closesAt()) {
            activeSessions.remove(state.id().value());
            Messenger.error(player, "The voting window has closed.");
            return 1;
        }

        String choice = StringArgumentType.getString(ctx, "choice");
        boolean yes;
        if (choice.equalsIgnoreCase("yes")) { yes = true; }
        else if (choice.equalsIgnoreCase("no")) { yes = false; }
        else { Messenger.error(player, "Use §e/state session vote yes§f or §e/state session vote no§f."); return 1; }

        session.votes().put(player.getUUID(), yes);
        Messenger.success(player, "Your vote has been recorded.");
        return 1;
    }

    private static int executeSessionResult(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        StateSession session = activeSessions.get(state.id().value());
        if (session == null) {
            Messenger.info(player, "No active session.");
            return 1;
        }

        int yesVotes = (int) session.votes().values().stream().filter(v -> v).count();
        int noVotes = (int) session.votes().values().stream().filter(v -> !v).count();
        int total = yesVotes + noVotes;

        if (System.currentTimeMillis() > session.closesAt() || total >= session.quorum()) {
            boolean passed = yesVotes > noVotes && total >= session.quorum();
            activeSessions.remove(state.id().value());
            NeoForge.EVENT_BUS.post(new StateSessionResultEvent(state, session.topic(), passed));
            Messenger.success(player, "Session §e" + session.topic() + " §f" + (passed ? "§aPASSED" : "§cFAILED")
                + "§f (§a" + yesVotes + "§f/§c" + noVotes + "§f, quorum: " + session.quorum() + ")");
        } else {
            Messenger.info(player, "Session §e" + session.topic() + "§f: §a" + yesVotes + "§f yes, §c" + noVotes + "§f no. Need " + session.quorum() + " total votes.");
        }
        return 1;
    }

    // ── Treasury ────────────────────────────────────────────────────────────

    private static int executeDeposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (!TownCommandHelper.deductFromInventory(player, amount)) {
            Messenger.error(player, "You don't have " + amount + " emeralds.");
            return 1;
        }
        var level = player.serverLevel();
        if (!TreasuryManager.depositToTreasury(level, state.treasuryChestPos(), amount)) {
            player.addItem(new ItemStack(Items.EMERALD, amount));
            Messenger.error(player, "State treasury chest is full or missing.");
            return 1;
        }
        Messenger.success(player, "Deposited " + amount + " ✦ to state treasury.");
        return 1;
    }

    private static int executeWithdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null || !requireChancellor(player, state)) return 1;

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        var level = player.serverLevel();
        if (!TreasuryManager.deductFromTreasury(level, state.treasuryChestPos(), amount)) {
            Messenger.error(player, "State treasury doesn't have " + amount + " emeralds.");
            return 1;
        }
        player.addItem(new ItemStack(Items.EMERALD, amount));
        Messenger.success(player, "Withdrew " + amount + " ✦ from state treasury.");
        return 1;
    }

    private static int executeBalance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        var level = player.serverLevel();
        long balance = TreasuryManager.scanTreasury(level, state.treasuryChestPos());
        Messenger.info(player, "State §d" + state.name() + " §ftreasury: §e" + balance + " ✦");
        return 1;
    }

    // ── Spawn ───────────────────────────────────────────────────────────────

    private static int executeSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state = requireState(player);
        if (state == null) return 1;

        if (state.townIds().isEmpty()) {
            Messenger.error(player, "State has no member towns.");
            return 1;
        }

        // Teleport to the first town's home chunk
        var firstTownId = state.townIds().iterator().next();
        TownData town = NeoTownsCache.getTown(firstTownId.value());
        if (town == null || town.plots().isEmpty()) {
            Messenger.error(player, "Cannot determine spawn location.");
            return 1;
        }
        var homePlot = town.plots().values().iterator().next();
        var pos = homePlot.pos().getWorldPosition();
        player.teleportTo(player.serverLevel(), pos.getX() + 0.5, player.serverLevel().getHeight(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        Messenger.success(player, "Teleported to §d" + state.name() + "§f.");
        return 1;
    }

    // ── Info ────────────────────────────────────────────────────────────────

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        StateData state;
        if (name != null) {
            state = null;
            for (StateData s : NeoTownsCache.allStates()) {
                if (s.name().equalsIgnoreCase(name)) { state = s; break; }
            }
            if (state == null) { Messenger.error(player, "No state found."); return 1; }
        } else {
            state = requireState(player);
            if (state == null) return 1;
        }

        String chancellorName = ctx.getSource().getServer().getPlayerList().getPlayer(state.chancellorUUID()) != null
            ? ctx.getSource().getServer().getPlayerList().getPlayer(state.chancellorUUID()).getName().getString()
            : state.chancellorUUID().toString().substring(0, 8);
        player.sendSystemMessage(Component.literal("§5═══ §d" + state.name() + " §5═══"));
        player.sendSystemMessage(Component.literal("§7Chancellor: §f" + chancellorName));
        player.sendSystemMessage(Component.literal("§7Government: §f" + state.governmentType()));
        player.sendSystemMessage(Component.literal("§7Towns: §f" + state.townIds().size()));
        player.sendSystemMessage(Component.literal("§7Laws: §f" + state.laws().size()));
        player.sendSystemMessage(Component.literal("§7Cabinet: §f" + state.cabinet().size() + " members"));
        if (state.hasNation()) {
            String nn = NeoTownsCache.getNationName(state.nationId().value());
            if (nn != null) player.sendSystemMessage(Component.literal("§7Nation: §f" + nn));
        }
        player.sendSystemMessage(Component.literal("§5═══════════════"));
        return 1;
    }

    // ── List ────────────────────────────────────────────────────────────────

    private static int executeList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var all = NeoTownsCache.allStates();
        if (all.isEmpty()) {
            Messenger.info(player, "No states have been founded.");
            return 1;
        }
        player.sendSystemMessage(Component.literal("§5── States (" + all.size() + ") ──"));
        for (StateData s : all.stream().sorted(Comparator.comparing(s2 -> s2.name().toLowerCase())).toList()) {
            player.sendSystemMessage(Component.literal(" §d" + s.name() + " §7- §f" + s.townIds().size() + " towns, "
                + s.governmentType()));
        }
        return 1;
    }
}
