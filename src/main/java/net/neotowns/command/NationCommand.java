package net.neotowns.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.engine.TreasuryManager;
import net.neotowns.event.*;
import net.neotowns.model.*;
import net.neotowns.model.enums.DiplomacyStatus;
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
public final class NationCommand {

    private NationCommand() {}

    // ── War tracking ────────────────────────────────────────────────────────
    private record ActiveWar(UUID aggressorId, UUID defenderId, long declaredAt, Long startedAt, int aggressorScore, int defenderScore, String status) {}
    private static final Map<UUID, ActiveWar> activeWars = new ConcurrentHashMap<>();

    // ── Vassal tracking ─────────────────────────────────────────────────────
    private static final Map<UUID, UUID> vassalToSuzerain = new ConcurrentHashMap<>();

    // ── Coup tracking ───────────────────────────────────────────────────────
    private record CoupPetition(UUID targetId, Set<UUID> signers, long expiresAt) {}
    private static final Map<UUID, CoupPetition> coupPetitions = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> theocracyGracePeriods = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        var root = literal("nation")
            .executes(NationCommand::executeDashboard)
            .then(literal("help").executes(NationCommand::executeHelp))
            .then(literal("found").then(argument("name", StringArgumentType.word()).executes(NationCommand::executeFound)))
            .then(literal("disband").executes(NationCommand::executeDisband))
            .then(literal("invite").then(argument("state", StringArgumentType.word()).executes(NationCommand::executeInvite)))
            .then(literal("join").then(argument("name", StringArgumentType.word()).executes(NationCommand::executeJoin)))
            .then(literal("leave").executes(NationCommand::executeLeave))
            .then(literal("kick").then(argument("state", StringArgumentType.word()).executes(NationCommand::executeKick)))
            .then(literal("set")
                .then(literal("name").then(argument("v", StringArgumentType.word()).executes(c -> executeSet(c,"name"))))
                .then(literal("leader").then(argument("p", EntityArgument.player()).executes(c -> executeSet(c,"leader"))))
                .then(literal("leadertitle").then(argument("v", StringArgumentType.word()).executes(c -> executeSet(c,"leadertitle"))))
                .then(literal("tax").then(argument("v", IntegerArgumentType.integer(0)).executes(c -> executeSet(c,"tax"))))
                .then(literal("taxtype").then(argument("v", StringArgumentType.word()).executes(c -> executeSet(c,"taxtype"))))
                .then(literal("govtype").then(argument("v", StringArgumentType.word()).executes(c -> executeSet(c,"govtype"))))
                .then(literal("capital").then(argument("v", StringArgumentType.word()).executes(c -> executeSet(c,"capital"))))
                .then(literal("ideology").then(argument("v", StringArgumentType.greedyString()).executes(c -> executeSet(c,"ideology"))))
                .then(literal("anthem").then(argument("v", StringArgumentType.greedyString()).executes(c -> executeSet(c,"anthem")))))
            .then(literal("law")
                .then(literal("add").then(argument("n", StringArgumentType.word()).then(argument("t", StringArgumentType.greedyString()).executes(NationCommand::executeLawAdd))))
                .then(literal("remove").then(argument("n", StringArgumentType.word()).executes(NationCommand::executeLawRemove)))
                .then(literal("list").executes(NationCommand::executeLawList)))
            .then(literal("constitution")
                .then(literal("set").then(argument("t", StringArgumentType.greedyString()).executes(NationCommand::executeConstitutionSet)))
                .then(literal("view").executes(NationCommand::executeConstitutionView)))
            .then(literal("cabinet")
                .then(literal("add").then(argument("p", EntityArgument.player()).then(argument("r", StringArgumentType.word()).executes(NationCommand::executeCabinetAdd))))
                .then(literal("remove").then(argument("p", EntityArgument.player()).executes(NationCommand::executeCabinetRemove)))
                .then(literal("list").executes(NationCommand::executeCabinetList)))
            .then(literal("diplomacy")
                .then(literal("ally").then(argument("n", StringArgumentType.word()).executes(c -> executeDiplomacy(c, DiplomacyStatus.ALLY))))
                .then(literal("trade").then(argument("n", StringArgumentType.word()).executes(c -> executeDiplomacy(c, DiplomacyStatus.TRADE_PARTNER))))
                .then(literal("enemy").then(argument("n", StringArgumentType.word()).executes(c -> executeDiplomacy(c, DiplomacyStatus.ENEMY))))
                .then(literal("neutral").then(argument("n", StringArgumentType.word()).executes(c -> executeDiplomacy(c, DiplomacyStatus.NEUTRAL)))))
            .then(literal("vassal")
                .then(literal("offer").then(argument("n", StringArgumentType.word()).executes(NationCommand::executeVassalOffer)))
                .then(literal("accept").then(argument("n", StringArgumentType.word()).executes(NationCommand::executeVassalAccept)))
                .then(literal("renounce").executes(NationCommand::executeVassalRenounce)))
            .then(literal("war")
                .then(literal("declare").then(argument("n", StringArgumentType.word()).executes(NationCommand::executeWarDeclare)))
                .then(literal("surrender").executes(NationCommand::executeWarSurrender))
                .then(literal("truce").then(argument("n", StringArgumentType.word()).executes(NationCommand::executeWarTruce)))
                .then(literal("status").executes(NationCommand::executeWarStatus)))
            .then(literal("embassy")
                .then(literal("open").then(argument("town", StringArgumentType.word()).executes(NationCommand::executeEmbassyOpen)))
                .then(literal("close").then(argument("town", StringArgumentType.word()).executes(NationCommand::executeEmbassyClose)))
                .then(literal("list").executes(NationCommand::executeEmbassyList)))
            .then(literal("citizen")
                .then(literal("add").then(argument("p", EntityArgument.player()).executes(NationCommand::executeCitizenAdd)))
                .then(literal("revoke").then(argument("p", EntityArgument.player()).executes(NationCommand::executeCitizenRevoke)))
                .then(literal("list").executes(NationCommand::executeCitizenList)))
            .then(literal("claimleadership").executes(NationCommand::executeClaimLeadership))
            .then(literal("deposit").then(argument("a", IntegerArgumentType.integer(1)).executes(NationCommand::executeDeposit)))
            .then(literal("withdraw").then(argument("a", IntegerArgumentType.integer(1)).executes(NationCommand::executeWithdraw)))
            .then(literal("balance").executes(NationCommand::executeBalance))
            .then(literal("list").executes(NationCommand::executeList))
            .then(literal("info").executes(c -> executeInfo(c, null))
                .then(argument("name", StringArgumentType.word()).executes(c -> executeInfo(c, StringArgumentType.getString(c, "name")))))
            .then(literal("leaderboard").executes(NationCommand::executeLeaderboard));
        d.register(root);
    }

    private static NationData requireNation(ServerPlayer player) {
        var town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null || town.stateId() == null) { Messenger.error(player, "You are not in a nation."); return null; }
        var state = NeoTownsCache.getState(town.stateId().value());
        if (state == null || state.nationId() == null) { Messenger.error(player, "Your state is not in a nation."); return null; }
        var nation = NeoTownsCache.getNation(state.nationId().value());
        if (nation == null) Messenger.error(player, "Nation data not found.");
        return nation;
    }

    private static boolean requireLeader(ServerPlayer player, NationData nation) {
        if (!nation.isLeader(player.getUUID())) { Messenger.error(player, "Only the nation leader can do that."); return false; }
        return true;
    }

    private static NationData findNation(String name) {
        for (var n : NeoTownsCache.allNations()) if (n.name().equalsIgnoreCase(name)) return n;
        return null;
    }

    // ── Dashboard ───────────────────────────────────────────────────────────
    private static int executeDashboard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null) return 1;
        String leaderName = ctx.getSource().getServer().getPlayerList().getPlayer(nation.leaderUUID()) != null
            ? ctx.getSource().getServer().getPlayerList().getPlayer(nation.leaderUUID()).getName().getString()
            : "Unknown";
        player.sendSystemMessage(Component.literal("§4═══ §c" + nation.name() + " §4═══"));
        player.sendSystemMessage(Component.literal("§7Leader: §f" + leaderName));
        player.sendSystemMessage(Component.literal("§7Government: §f" + nation.governmentType()));
        player.sendSystemMessage(Component.literal("§7States: §f" + nation.stateIds().size()));
        player.sendSystemMessage(Component.literal("§7Ideology: §f" + (nation.ideology() != null ? nation.ideology() : "None")));
        return 1;
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("§4── Nation Commands ──"));
        player.sendSystemMessage(Component.literal("§c/nation §7— Dashboard"));
        player.sendSystemMessage(Component.literal("§c/nation found <name>§7— Found a nation"));
        player.sendSystemMessage(Component.literal("§c/nation invite|join|leave|kick§7— Membership"));
        player.sendSystemMessage(Component.literal("§c/nation set <k> <v>§7— Configure"));
        player.sendSystemMessage(Component.literal("§c/nation diplomacy <status> <n>§7— Relations"));
        player.sendSystemMessage(Component.literal("§c/nation war declare|surrender|truce§7— War"));
        player.sendSystemMessage(Component.literal("§c/nation vassal offer|accept|renounce§7— Vassalage"));
        return 1;
    }

    // ── Found ───────────────────────────────────────────────────────────────
    private static int executeFound(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null || town.stateId() == null) { Messenger.error(player, "Your town must be in a state."); return 1; }
        var state = NeoTownsCache.getState(town.stateId().value());
        if (state == null || !state.isChancellor(player.getUUID())) { Messenger.error(player, "Only a state chancellor can found a nation."); return 1; }
        if (state.nationId() != null) { Messenger.error(player, "Your state is already in a nation."); return 1; }

        String name = StringArgumentType.getString(ctx, "name");
        if (findNation(name) != null) { Messenger.error(player, "A nation with that name already exists."); return 1; }

        int cost = NeoTownsConfig.get().getNationFoundingCost();
        if (!TownCommandHelper.deductFromInventory(player, cost)) { Messenger.error(player, "You need " + cost + " emeralds."); return 1; }

        var chestPos = player.blockPosition();
        var be = player.serverLevel().getBlockEntity(chestPos);
        if (!(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)) {
            player.addItem(new ItemStack(Items.EMERALD, cost));
            Messenger.error(player, "Stand next to a chest to designate as nation treasury."); return 1;
        }

        NTId nationId = NTId.random();
        long now = System.currentTimeMillis() / 86400000L;
        var builder = new NationDataBuilder();
        builder.id = nationId; builder.name = name; builder.leaderUUID = player.getUUID();
        builder.stateIds = new HashSet<>(Set.of(state.id()));
        builder.treasuryChestPos = chestPos; builder.treasuryWorld = player.serverLevel().dimension();
        builder.nationTaxEmeralds = 0; builder.nationTaxType = TaxType.FLAT;
        builder.diplomacy = DiplomacyMap.empty(); builder.governmentType = GovernmentType.DEMOCRACY;
        builder.laws = new HashMap<>(); builder.constitution = "No constitution has been written.";
        builder.cabinet = new HashMap<>(); builder.ideology = ""; builder.anthem = "";
        builder.foundedEpochDay = now;
        NationData nation = builder.build();

        var stateBuilder = new StateDataBuilder(state);
        stateBuilder.nationId = nationId;
        NeoTownsCache.putState(stateBuilder.build());
        NeoTownsCache.putNation(nation);
        DatabaseManager.saveState(stateBuilder.build());
        DatabaseManager.saveNation(nation);
        NeoForge.EVENT_BUS.post(new NationFoundEvent(nation));
        Messenger.success(player, "Nation §c" + name + " §ffounded!");
        return 1;
    }

    // ── Disband / Invite / Join / Leave / Kick ──────────────────────────────
    private static int executeDisband(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        for (var sid : nation.stateIds()) { var s = NeoTownsCache.getState(sid.value()); if (s == null) continue;
            var sb = new StateDataBuilder(s); sb.nationId = null; NeoTownsCache.putState(sb.build()); DatabaseManager.saveState(sb.build()); }
        NeoTownsCache.removeNation(nation.id().value()); DatabaseManager.deleteNation(nation.id().value());
        NeoForge.EVENT_BUS.post(new NationDisbandEvent(nation));
        Messenger.success(player, "Nation disbanded.");
        return 1;
    }

    private static int executeInvite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        String sname = StringArgumentType.getString(ctx, "state");
        StateData target = null;
        for (var s : NeoTownsCache.allStates()) if (s.name().equalsIgnoreCase(sname) && s.nationId() == null) { target = s; break; }
        if (target == null) { Messenger.error(player, "No unaffiliated state found with that name."); return 1; }
        var chancellor = ctx.getSource().getServer().getPlayerList().getPlayer(target.chancellorUUID());
        if (chancellor != null) Messenger.info(chancellor, "Your state is invited to join §c" + nation.name() + "§f. Use §e/nation join " + nation.name());
        Messenger.success(player, "Invited §b" + target.name() + "§f.");
        return 1;
    }

    private static int executeJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null || town.stateId() == null) { Messenger.error(player, "Your town must be in a state."); return 1; }
        var state = NeoTownsCache.getState(town.stateId().value());
        if (state == null || !state.isChancellor(player.getUUID())) { Messenger.error(player, "Only a chancellor can join a nation."); return 1; }
        if (state.nationId() != null) { Messenger.error(player, "Your state is already in a nation."); return 1; }
        var nation = findNation(StringArgumentType.getString(ctx, "name"));
        if (nation == null) { Messenger.error(player, "No nation found."); return 1; }
        var sb = new StateDataBuilder(state); sb.nationId = nation.id();
        var nb = new NationDataBuilder(nation); var newIds = new HashSet<>(nation.stateIds()); newIds.add(state.id()); nb.stateIds = Collections.unmodifiableSet(newIds);
        NeoTownsCache.putState(sb.build()); NeoTownsCache.putNation(nb.build());
        DatabaseManager.saveState(sb.build()); DatabaseManager.saveNation(nb.build());
        NeoForge.EVENT_BUS.post(new NationMembershipEvent(nb.build(), sb.build(), true));
        Messenger.success(player, "Your state joined §c" + nation.name() + "§f.");
        return 1;
    }

    private static int executeLeave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null || town.stateId() == null) { Messenger.error(player, "Your town must be in a state."); return 1; }
        var state = NeoTownsCache.getState(town.stateId().value());
        if (state == null || !state.isChancellor(player.getUUID())) { Messenger.error(player, "Only a chancellor can leave a nation."); return 1; }
        if (state.nationId() == null) { Messenger.error(player, "Your state is not in a nation."); return 1; }
        var nation = NeoTownsCache.getNation(state.nationId().value()); if (nation == null) return 1;
        var sb = new StateDataBuilder(state); sb.nationId = null;
        var nb = new NationDataBuilder(nation); var newIds = new HashSet<>(nation.stateIds()); newIds.remove(state.id()); nb.stateIds = Collections.unmodifiableSet(newIds);
        NeoTownsCache.putState(sb.build()); NeoTownsCache.putNation(nb.build());
        DatabaseManager.saveState(sb.build()); DatabaseManager.saveNation(nb.build());
        NeoForge.EVENT_BUS.post(new NationMembershipEvent(nb.build(), sb.build(), false));
        Messenger.success(player, "Your state left §c" + nation.name() + "§f.");
        return 1;
    }

    private static int executeKick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        String sname = StringArgumentType.getString(ctx, "state");
        StateData target = null;
        for (var s : NeoTownsCache.allStates()) if (s.name().equalsIgnoreCase(sname) && s.nationId() != null && s.nationId().equals(nation.id())) { target = s; break; }
        if (target == null) { Messenger.error(player, "No member state found."); return 1; }
        var sb = new StateDataBuilder(target); sb.nationId = null;
        var nb = new NationDataBuilder(nation); var newIds = new HashSet<>(nation.stateIds()); newIds.remove(target.id()); nb.stateIds = Collections.unmodifiableSet(newIds);
        NeoTownsCache.putState(sb.build()); NeoTownsCache.putNation(nb.build());
        DatabaseManager.saveState(sb.build()); DatabaseManager.saveNation(nb.build());
        NeoForge.EVENT_BUS.post(new NationMembershipEvent(nb.build(), sb.build(), false));
        Messenger.success(player, "Kicked §b" + target.name() + "§f.");
        return 1;
    }

    // ── Set ─────────────────────────────────────────────────────────────────
    private static int executeSet(CommandContext<CommandSourceStack> ctx, String field) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var b = new NationDataBuilder(nation);
        switch (field) {
            case "name" -> { b.name = StringArgumentType.getString(ctx, "v"); Messenger.success(player, "Nation renamed."); }
            case "leader" -> { var t = EntityArgument.getPlayer(ctx, "p"); b.leaderUUID = t.getUUID(); Messenger.success(player, "Leadership transferred."); }
            case "tax" -> { b.nationTaxEmeralds = IntegerArgumentType.getInteger(ctx, "v"); Messenger.success(player, "Tax updated."); }
            case "taxtype" -> { try { b.nationTaxType = TaxType.valueOf(StringArgumentType.getString(ctx, "v").toUpperCase()); Messenger.success(player, "Tax type updated."); } catch (Exception e) { Messenger.error(player, "Invalid tax type."); return 1; }}
            case "govtype" -> { try { b.governmentType = GovernmentType.valueOf(StringArgumentType.getString(ctx, "v").toUpperCase()); Messenger.success(player, "Government type changed."); } catch (Exception e) { Messenger.error(player, "Invalid government type."); return 1; }}
            case "ideology" -> { b.ideology = StringArgumentType.getString(ctx, "v"); Messenger.success(player, "Ideology updated."); }
            case "anthem" -> { b.anthem = StringArgumentType.getString(ctx, "v"); Messenger.success(player, "Anthem updated."); }
            default -> { Messenger.info(player, "Setting not implemented."); return 1; }
        }
        NeoTownsCache.putNation(b.build()); DatabaseManager.saveNation(b.build()); return 1;
    }

    // ── Laws ────────────────────────────────────────────────────────────────
    private static int executeLawAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var b = new NationDataBuilder(nation); var laws = new HashMap<>(nation.laws());
        laws.put(StringArgumentType.getString(ctx, "n"), StringArgumentType.getString(ctx, "t"));
        b.laws = Collections.unmodifiableMap(laws);
        NeoTownsCache.putNation(b.build()); DatabaseManager.saveNation(b.build());
        NeoForge.EVENT_BUS.post(new LawEnactedEvent(b.build(), StringArgumentType.getString(ctx, "n"), StringArgumentType.getString(ctx, "t")));
        Messenger.success(player, "Law enacted."); return 1;
    }
    private static int executeLawRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var b = new NationDataBuilder(nation); var laws = new HashMap<>(nation.laws());
        laws.remove(StringArgumentType.getString(ctx, "n"));
        b.laws = Collections.unmodifiableMap(laws);
        NeoTownsCache.putNation(b.build()); DatabaseManager.saveNation(b.build());
        NeoForge.EVENT_BUS.post(new LawRepealedEvent(b.build(), StringArgumentType.getString(ctx, "n")));
        Messenger.success(player, "Law repealed."); return 1;
    }
    private static int executeLawList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player); if (nation == null) return 1;
        if (nation.laws().isEmpty()) { Messenger.info(player, "No laws."); return 1; }
        player.sendSystemMessage(Component.literal("§4── Laws ──"));
        nation.laws().forEach((k, v) -> player.sendSystemMessage(Component.literal(" §c" + k + "§7: §f" + v)));
        return 1;
    }

    // ── Constitution ────────────────────────────────────────────────────────
    private static int executeConstitutionSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player);
        if (nation == null || !requireLeader(player, nation)) return 1;
        var b = new NationDataBuilder(nation); b.constitution = StringArgumentType.getString(ctx, "t");
        NeoTownsCache.putNation(b.build()); DatabaseManager.saveNation(b.build());
        Messenger.success(player, "Constitution updated."); return 1;
    }
    private static int executeConstitutionView(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player); if (nation == null) return 1;
        player.sendSystemMessage(Component.literal("§4── Constitution ──§f " + (nation.constitution() != null ? nation.constitution() : "None")));
        return 1;
    }

    // ── Cabinet ─────────────────────────────────────────────────────────────
    private static int executeCabinetAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player);
        if (nation == null || !requireLeader(player, nation)) return 1;
        var target = EntityArgument.getPlayer(ctx, "p"); var role = StringArgumentType.getString(ctx, "r");
        var cab = new HashMap<>(nation.cabinet()); cab.put(target.getUUID(), role);
        var b = new NationDataBuilder(nation); b.cabinet = Collections.unmodifiableMap(cab);
        NeoTownsCache.putNation(b.build()); DatabaseManager.saveNation(b.build());
        Messenger.success(player, "Appointed §b" + target.getName().getString() + " §fas §a" + role + "§f."); return 1;
    }
    private static int executeCabinetRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player);
        if (nation == null || !requireLeader(player, nation)) return 1;
        var target = EntityArgument.getPlayer(ctx, "p");
        var cab = new HashMap<>(nation.cabinet()); cab.remove(target.getUUID());
        var b = new NationDataBuilder(nation); b.cabinet = Collections.unmodifiableMap(cab);
        NeoTownsCache.putNation(b.build()); DatabaseManager.saveNation(b.build());
        Messenger.success(player, "Removed §b" + target.getName().getString() + "§f."); return 1;
    }
    private static int executeCabinetList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player); if (nation == null) return 1;
        if (nation.cabinet().isEmpty()) { Messenger.info(player, "No cabinet."); return 1; }
        player.sendSystemMessage(Component.literal("§4── Cabinet ──"));
        nation.cabinet().forEach((uuid, role) -> {
            String n = ctx.getSource().getServer().getPlayerList().getPlayer(uuid) != null
                ? ctx.getSource().getServer().getPlayerList().getPlayer(uuid).getName().getString() : uuid.toString().substring(0, 8);
            player.sendSystemMessage(Component.literal(" §d" + role + "§7: §f" + n));
        }); return 1;
    }

    // ── Diplomacy ───────────────────────────────────────────────────────────
    private static int executeDiplomacy(CommandContext<CommandSourceStack> ctx, DiplomacyStatus status) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var target = findNation(StringArgumentType.getString(ctx, "n"));
        if (target == null || target.id().equals(nation.id())) { Messenger.error(player, "Nation not found."); return 1; }
        var oldStatus = nation.getDiplomaticStatus(target.id());
        var newDiplo = nation.diplomacy().withRelation(target.id(), status);
        var targetNewDiplo = target.diplomacy().withRelation(nation.id(), status);
        var nb = new NationDataBuilder(nation); nb.diplomacy = newDiplo;
        var tb = new NationDataBuilder(target); tb.diplomacy = targetNewDiplo;
        NeoTownsCache.putNation(nb.build()); DatabaseManager.saveNation(nb.build());
        NeoTownsCache.putNation(tb.build()); DatabaseManager.saveNation(tb.build());
        NeoForge.EVENT_BUS.post(new DiplomacyChangeEvent(nation, target, oldStatus, status));
        Messenger.success(player, "Diplomacy with §c" + target.name() + " §fset to §a" + status + "§f.");
        return 1;
    }

    // ── Vassal ──────────────────────────────────────────────────────────────
    private static int executeVassalOffer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var target = findNation(StringArgumentType.getString(ctx, "n"));
        if (target == null) { Messenger.error(player, "Nation not found."); return 1; }
        // For simplicity, immediate acceptance
        vassalToSuzerain.put(target.id().value(), nation.id().value());
        var nb = new NationDataBuilder(nation);
        nb.diplomacy = nation.diplomacy().withRelation(target.id(), DiplomacyStatus.SUZERAIN);
        var tb = new NationDataBuilder(target);
        tb.diplomacy = target.diplomacy().withRelation(nation.id(), DiplomacyStatus.VASSAL);
        NeoTownsCache.putNation(nb.build()); NeoTownsCache.putNation(tb.build());
        DatabaseManager.saveNation(nb.build()); DatabaseManager.saveNation(tb.build());
        Messenger.success(player, "§b" + target.name() + " §fis now your vassal.");
        return 1;
    }
    private static int executeVassalAccept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // Already handled by offer for now
        Messenger.info(ctx.getSource().getPlayerOrException(), "Vassalage accepted.");
        return 1;
    }
    private static int executeVassalRenounce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        UUID suzerainId = vassalToSuzerain.remove(nation.id().value());
        if (suzerainId == null) { Messenger.error(player, "Your nation is not a vassal."); return 1; }
        var suzerain = NeoTownsCache.getNation(suzerainId);
        if (suzerain != null) {
            var sb = new NationDataBuilder(suzerain);
            sb.diplomacy = suzerain.diplomacy().withRelation(nation.id(), DiplomacyStatus.NEUTRAL);
            NeoTownsCache.putNation(sb.build()); DatabaseManager.saveNation(sb.build());
        }
        var nb = new NationDataBuilder(nation);
        nb.diplomacy = nation.diplomacy().withRelation(new NTId(suzerainId), DiplomacyStatus.NEUTRAL);
        NeoTownsCache.putNation(nb.build()); DatabaseManager.saveNation(nb.build());
        Messenger.success(player, "You have renounced vassalage.");
        return 1;
    }

    // ── War ─────────────────────────────────────────────────────────────────
    private static int executeWarDeclare(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var target = findNation(StringArgumentType.getString(ctx, "n"));
        if (target == null || target.id().equals(nation.id())) { Messenger.error(player, "Nation not found."); return 1; }
        if (activeWars.containsKey(nation.id().value()) || activeWars.containsKey(target.id().value())) {
            Messenger.error(player, "One of these nations is already at war."); return 1;
        }
        int cost = NeoTownsConfig.get().getWarDeclarationCost();
        if (!TownCommandHelper.deductFromInventory(player, cost)) { Messenger.error(player, "You need " + cost + " emeralds."); return 1; }
        long now = System.currentTimeMillis();
        long warningEnd = now + NeoTownsConfig.get().getWarWarningPeriodHours() * 3600000L;
        activeWars.put(nation.id().value(), new ActiveWar(nation.id().value(), target.id().value(), now, warningEnd, 0, 0, "WARNING"));
        activeWars.put(target.id().value(), new ActiveWar(target.id().value(), nation.id().value(), now, warningEnd, 0, 0, "WARNING"));
        var nb = new NationDataBuilder(nation);
        nb.diplomacy = nation.diplomacy().withRelation(target.id(), DiplomacyStatus.WAR);
        var tb = new NationDataBuilder(target);
        tb.diplomacy = target.diplomacy().withRelation(nation.id(), DiplomacyStatus.WAR);
        NeoTownsCache.putNation(nb.build()); NeoTownsCache.putNation(tb.build());
        DatabaseManager.saveNation(nb.build()); DatabaseManager.saveNation(tb.build());
        NeoForge.EVENT_BUS.post(new WarDeclaredEvent(nation, target));
        Messenger.success(player, "War declared on §c" + target.name() + "§f! 24h warning period active.");
        return 1;
    }
    private static int executeWarSurrender(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var war = activeWars.get(nation.id().value());
        if (war == null) { Messenger.error(player, "Your nation is not at war."); return 1; }
        UUID enemyId = war.aggressorId().equals(nation.id().value()) ? war.defenderId() : war.aggressorId();
        var enemy = NeoTownsCache.getNation(enemyId);
        if (enemy == null) return 1;
        // Check if warning period — if so, no penalty
        long penalty = System.currentTimeMillis() < war.startedAt() ? 0 : NeoTownsConfig.get().getWarSurrenderPenalty();
        if (penalty > 0 && !TownCommandHelper.deductFromInventory(player, (int) penalty)) {
            Messenger.error(player, "You need " + penalty + " emeralds for the surrender penalty.");
            return 1;
        }
        activeWars.remove(nation.id().value()); activeWars.remove(enemyId);
        resetDiplomacy(nation, enemy);
        NeoForge.EVENT_BUS.post(new WarEndedEvent(enemy, nation, true));
        Messenger.success(player, "You surrendered to §c" + enemy.name() + "§f.");
        return 1;
    }
    private static int executeWarTruce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var war = activeWars.get(nation.id().value());
        if (war == null) { Messenger.error(player, "Not at war."); return 1; }
        UUID enemyId = war.aggressorId().equals(nation.id().value()) ? war.defenderId() : war.aggressorId();
        var enemy = NeoTownsCache.getNation(enemyId);
        if (enemy == null) return 1;
        activeWars.remove(nation.id().value()); activeWars.remove(enemyId);
        resetDiplomacy(nation, enemy);
        NeoForge.EVENT_BUS.post(new WarEndedEvent(nation, enemy, false));
        Messenger.success(player, "Truce signed with §c" + enemy.name() + "§f.");
        return 1;
    }
    private static int executeWarStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null) return 1;
        var war = activeWars.get(nation.id().value());
        if (war == null) { Messenger.info(player, "Your nation is not at war."); return 1; }
        UUID enemyId = war.aggressorId().equals(nation.id().value()) ? war.defenderId() : war.aggressorId();
        var enemy = NeoTownsCache.getNation(enemyId);
        player.sendSystemMessage(Component.literal("§cWar with §f" + (enemy != null ? enemy.name() : "Unknown")));
        player.sendSystemMessage(Component.literal("§7Status: §f" + war.status()));
        player.sendSystemMessage(Component.literal("§7Score - You: §f" + war.aggressorScore() + " §7Enemy: §f" + war.defenderScore()));
        return 1;
    }
    private static void resetDiplomacy(NationData a, NationData b) {
        var ab = new NationDataBuilder(a); ab.diplomacy = a.diplomacy().withRelation(b.id(), DiplomacyStatus.NEUTRAL);
        var bb = new NationDataBuilder(b); bb.diplomacy = b.diplomacy().withRelation(a.id(), DiplomacyStatus.NEUTRAL);
        NeoTownsCache.putNation(ab.build()); DatabaseManager.saveNation(ab.build());
        NeoTownsCache.putNation(bb.build()); DatabaseManager.saveNation(bb.build());
    }

    // ── Embassy ─────────────────────────────────────────────────────────────
    private static int executeEmbassyOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        Messenger.info(player, "Embassy system not yet implemented — use §e/town plot set type EMBASSY");
        return 1;
    }
    private static int executeEmbassyClose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        Messenger.info(player, "Embassy system not yet fully implemented.");
        return 1;
    }
    private static int executeEmbassyList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null) return 1;
        Messenger.info(player, "No embassies established.");
        return 1;
    }

    // ── Citizenship ─────────────────────────────────────────────────────────
    private static final Map<UUID, Set<UUID>> nationCitizens = new ConcurrentHashMap<>();
    private static int executeCitizenAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var target = EntityArgument.getPlayer(ctx, "p");
        nationCitizens.computeIfAbsent(nation.id().value(), k -> ConcurrentHashMap.newKeySet()).add(target.getUUID());
        Messenger.success(player, "Added §b" + target.getName().getString() + " §fas a national citizen.");
        return 1;
    }
    private static int executeCitizenRevoke(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null || !requireLeader(player, nation)) return 1;
        var target = EntityArgument.getPlayer(ctx, "p");
        var set = nationCitizens.get(nation.id().value());
        if (set != null) set.remove(target.getUUID());
        Messenger.success(player, "Revoked citizenship from §b" + target.getName().getString() + "§f.");
        return 1;
    }
    private static int executeCitizenList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null) return 1;
        var set = nationCitizens.get(nation.id().value());
        if (set == null || set.isEmpty()) { Messenger.info(player, "No national citizens."); return 1; }
        player.sendSystemMessage(Component.literal("§4── Citizens ──"));
        set.forEach(uuid -> {
            String n = ctx.getSource().getServer().getPlayerList().getPlayer(uuid) != null
                ? ctx.getSource().getServer().getPlayerList().getPlayer(uuid).getName().getString() : uuid.toString().substring(0, 8);
            player.sendSystemMessage(Component.literal(" §f- " + n));
        }); return 1;
    }

    // ── Theocracy Claim Leadership ──────────────────────────────────────────
    private static int executeClaimLeadership(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var nation = requireNation(player); if (nation == null) return 1;
        if (nation.governmentType() != GovernmentType.THEOCRACY) {
            Messenger.error(player, "Only theocracies can claim leadership this way."); return 1;
        }
        var mainHand = player.getMainHandItem();
        var offHand = player.getOffhandItem();
        if (!mainHand.is(Items.NETHER_STAR) && !offHand.is(Items.NETHER_STAR)) {
            Messenger.error(player, "You must hold a Nether Star to claim leadership."); return 1;
        }
        var b = new NationDataBuilder(nation); b.leaderUUID = player.getUUID();
        NeoTownsCache.putNation(b.build()); DatabaseManager.saveNation(b.build());
        Messenger.success(player, "You claimed leadership of §c" + nation.name() + "§f!");
        return 1;
    }

    // ── Treasury ────────────────────────────────────────────────────────────
    private static int executeDeposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player); if (nation == null) return 1;
        int amount = IntegerArgumentType.getInteger(ctx, "a");
        if (!TownCommandHelper.deductFromInventory(player, amount)) { Messenger.error(player, "Not enough emeralds."); return 1; }
        if (!TreasuryManager.depositToTreasury(player.serverLevel(), nation.treasuryChestPos(), amount)) {
            player.addItem(new ItemStack(Items.EMERALD, amount)); Messenger.error(player, "Treasury full or missing."); return 1;
        }
        Messenger.success(player, "Deposited " + amount + " ✦.");
        return 1;
    }
    private static int executeWithdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player);
        if (nation == null || !requireLeader(player, nation)) return 1;
        int amount = IntegerArgumentType.getInteger(ctx, "a");
        if (!TreasuryManager.deductFromTreasury(player.serverLevel(), nation.treasuryChestPos(), amount)) {
            Messenger.error(player, "Insufficient funds."); return 1;
        }
        player.addItem(new ItemStack(Items.EMERALD, amount));
        Messenger.success(player, "Withdrew " + amount + " ✦.");
        return 1;
    }
    private static int executeBalance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException(); var nation = requireNation(player); if (nation == null) return 1;
        long bal = TreasuryManager.scanTreasury(player.serverLevel(), nation.treasuryChestPos());
        Messenger.info(player, "Nation treasury: §e" + bal + " ✦");
        return 1;
    }

    // ── Info / List / Leaderboard ───────────────────────────────────────────
    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        NationData nation;
        if (name != null) { nation = findNation(name); if (nation == null) { Messenger.error(player, "Nation not found."); return 1; } }
        else { nation = requireNation(player); if (nation == null) return 1; }
        String leaderName = ctx.getSource().getServer().getPlayerList().getPlayer(nation.leaderUUID()) != null
            ? ctx.getSource().getServer().getPlayerList().getPlayer(nation.leaderUUID()).getName().getString()
            : nation.leaderUUID().toString().substring(0, 8);
        player.sendSystemMessage(Component.literal("§4═══ §c" + nation.name() + " §4═══"));
        player.sendSystemMessage(Component.literal("§7Leader: §f" + leaderName));
        player.sendSystemMessage(Component.literal("§7Government: §f" + nation.governmentType()));
        player.sendSystemMessage(Component.literal("§7States: §f" + nation.stateIds().size()));
        if (nation.ideology() != null && !nation.ideology().isEmpty())
            player.sendSystemMessage(Component.literal("§7Ideology: §f" + nation.ideology()));
        if (nation.anthem() != null && !nation.anthem().isEmpty())
            player.sendSystemMessage(Component.literal("§7Anthem: §f" + nation.anthem()));
        return 1;
    }
    private static int executeList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var all = NeoTownsCache.allNations();
        if (all.isEmpty()) { Messenger.info(player, "No nations."); return 1; }
        player.sendSystemMessage(Component.literal("§4── Nations (" + all.size() + ") ──"));
        all.stream().sorted(Comparator.comparing(n -> n.name().toLowerCase())).forEach(n ->
            player.sendSystemMessage(Component.literal(" §c" + n.name() + " §7- §f" + n.stateIds().size() + " states")));
        return 1;
    }
    private static int executeLeaderboard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        var sorted = NeoTownsCache.allNations().stream()
            .sorted(Comparator.<NationData>comparingInt(n -> n.stateIds().size()).reversed())
            .toList();
        if (sorted.isEmpty()) { Messenger.info(player, "No nations."); return 1; }
        player.sendSystemMessage(Component.literal("§4── Nation Leaderboard ──"));
        int rank = 1;
        for (var n : sorted) {
            int totalTowns = (int) n.stateIds().stream().map(sid -> {
                var s = NeoTownsCache.getState(sid.value()); return s != null ? s.townIds().size() : 0;
            }).count();
            player.sendSystemMessage(Component.literal(" §" + (rank <= 3 ? "6" : "7") + (rank++) + ". §c" + n.name()
                + " §7- §f" + totalTowns + " towns, " + n.stateIds().size() + " states"));
        }
        return 1;
    }
}
