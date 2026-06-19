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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.ChunkOwnershipCache;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.engine.TreasuryManager;
import net.neotowns.event.ClaimEvent;
import net.neotowns.event.TownDisbandEvent;
import net.neotowns.event.TownFoundEvent;
import net.neotowns.event.TownJoinEvent;
import net.neotowns.event.TownLeaveEvent;
import net.neotowns.model.NTId;
import net.neotowns.model.PlotData;
import net.neotowns.model.TownData;
import net.neotowns.model.TownPerms;
import net.neotowns.model.TownDataBuilder;
import net.neotowns.model.enums.PlotType;
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
public final class TownCommand {

    private static final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> spawnCooldowns = new ConcurrentHashMap<>();

    private record PendingInvite(UUID townId, long expiresAt) {}

    private TownCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(literal("town")
            .executes(TownCommand::executeDashboard)
            .then(literal("help").executes(TownCommand::executeHelp))
            .then(literal("found")
                .then(argument("name", StringArgumentType.word())
                    .executes(TownCommand::executeFound)))
            .then(literal("disband")
                .executes(TownCommand::executeDisband))
            .then(literal("claim")
                .executes(TownCommand::executeClaim)
                .then(literal("outpost").executes(TownCommand::executeClaimOutpost)))
            .then(literal("unclaim")
                .executes(TownCommand::executeUnclaim))
            .then(literal("invite")
                .then(argument("player", EntityArgument.player())
                    .executes(TownCommand::executeInvite)))
            .then(literal("join")
                .then(argument("name", StringArgumentType.word())
                    .executes(TownCommand::executeJoin)))
            .then(literal("leave")
                .executes(TownCommand::executeLeave))
            .then(literal("kick")
                .then(argument("player", EntityArgument.player())
                    .executes(TownCommand::executeKick)))
            .then(literal("deposit")
                .then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(TownCommand::executeDeposit)))
            .then(literal("withdraw")
                .then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(TownCommand::executeWithdraw)))
            .then(literal("balance")
                .executes(TownCommand::executeBalance))
            .then(literal("spawn")
                .executes(ctx -> executeSpawn(ctx, null))
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> executeSpawn(ctx, StringArgumentType.getString(ctx, "name")))))
            .then(literal("map")
                .executes(TownCommand::executeMap))
            .then(literal("info")
                .executes(ctx -> executeInfo(ctx, null))
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> executeInfo(ctx, StringArgumentType.getString(ctx, "name")))))
            .then(literal("list")
                .executes(TownCommand::executeList))
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static boolean isAdjacent(ChunkPos pos, Collection<ChunkPos> existing) {
        for (ChunkPos e : existing) {
            if (Math.abs(pos.x - e.x) + Math.abs(pos.z - e.z) == 1) return true;
        }
        return false;
    }

    private static int maxClaims(TownData town) {
        int calculated = NeoTownsConfig.get().getBaseClaims()
            + town.residentUUIDs().size() * NeoTownsConfig.get().getClaimsPerResident();
        return Math.min(calculated, NeoTownsConfig.get().getMaxClaimsHardCap());
    }

    private static BlockPos findNearbyChest(ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    BlockEntity be = player.serverLevel().getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity) return pos;
                }
            }
        }
        return null;
    }

    // ── Dashboard ───────────────────────────────────────────────────────────

    private static int executeDashboard(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = NeoTownsCache.getTownByPlayer(player.getUUID());
        if (town == null) {
            Messenger.info(player, "You are not in a town. Use §e/town found <name>§f to start one.");
            return 1;
        }
        String mayorName = ctx.getSource().getServer().getPlayerList().getPlayer(town.mayorUUID()) != null
            ? ctx.getSource().getServer().getPlayerList().getPlayer(town.mayorUUID()).getName().getString()
            : "Unknown";
        player.sendSystemMessage(Component.literal("§6═══ §e" + town.name() + " §6═══"));
        player.sendSystemMessage(Component.literal("§7Mayor: §f" + mayorName));
        player.sendSystemMessage(Component.literal("§7Residents: §f" + town.residentUUIDs().size()));
        player.sendSystemMessage(Component.literal("§7Claims: §f" + town.claimCount() + "§7/§f" + maxClaims(town)));
        player.sendSystemMessage(Component.literal("§7Treasury: §2" + town.emeraldBalance() + " ✦"));
        if (town.hasState()) {
            String stateName = NeoTownsCache.getStateName(town.stateId().value());
            if (stateName != null) player.sendSystemMessage(Component.literal("§7State: §f" + stateName));
        }
        player.sendSystemMessage(Component.literal("§6══════════════"));
        return 1;
    }

    // ── Help ────────────────────────────────────────────────────────────────

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("§6── Town Commands ──"));
        player.sendSystemMessage(Component.literal("§e/town §7— Dashboard"));
        player.sendSystemMessage(Component.literal("§e/town found <name>§7— Found a town"));
        player.sendSystemMessage(Component.literal("§e/town disband§7— Disband (Mayor)"));
        player.sendSystemMessage(Component.literal("§e/town claim [outpost]§7— Claim chunk"));
        player.sendSystemMessage(Component.literal("§e/town unclaim§7— Unclaim chunk"));
        player.sendSystemMessage(Component.literal("§e/town invite/join/leave/kick§7— Member management"));
        player.sendSystemMessage(Component.literal("§e/town set <setting> <value>§7— Configure town"));
        player.sendSystemMessage(Component.literal("§e/town ally add/remove/list§7— Ally management"));
        player.sendSystemMessage(Component.literal("§e/town plot <action>§7— Plot management"));
        player.sendSystemMessage(Component.literal("§e/town deposit/withdraw/balance§7— Treasury"));
        player.sendSystemMessage(Component.literal("§e/town spawn§7— Teleport home"));
        player.sendSystemMessage(Component.literal("§e/town map§7— ASCII chunk map"));
        player.sendSystemMessage(Component.literal("§e/town info/list§7— Information"));
        return 1;
    }

    // ── Found ───────────────────────────────────────────────────────────────

    private static int executeFound(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");

        if (NeoTownsCache.getTownByPlayer(player.getUUID()) != null) {
            Messenger.error(player, "You are already in a town.");
            return 1;
        }
        if (name.length() < 2 || name.length() > 30) {
            Messenger.error(player, "Town name must be 2-30 characters.");
            return 1;
        }
        for (TownData t : NeoTownsCache.allTowns()) {
            if (t.name().equalsIgnoreCase(name)) {
                Messenger.error(player, "A town with that name already exists.");
                return 1;
            }
        }
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        if (ChunkOwnershipCache.getOwner(player.serverLevel(), chunk) != null) {
            Messenger.error(player, "This chunk is already claimed.");
            return 1;
        }
        BlockPos chestPos = findNearbyChest(player);
        if (chestPos == null) {
            Messenger.error(player, "Place a chest within 5 blocks first.");
            return 1;
        }
        int cost = NeoTownsConfig.get().getTownFoundingCost();
        if (!TownCommandHelper.deductFromInventory(player, cost)) {
            Messenger.error(player, "You need " + cost + " emeralds to found a town.");
            return 1;
        }

        NTId townId = NTId.random();
        long now = System.currentTimeMillis() / 86400000L;
        var plots = new HashMap<String, PlotData>();
        plots.put(chunk.toString(), new PlotData(chunk, player.serverLevel().dimension(), townId, PlotType.DEFAULT, null, 0, false));

        TownData town = new TownData(
            townId, name, player.getUUID(),
            new HashSet<>(Set.of(player.getUUID())), new HashSet<>(),
            null, chestPos, player.serverLevel().dimension(), 0L,
            NeoTownsConfig.get().getTownUpkeepPerChunk(), 0L, TaxType.FLAT,
            NeoTownsConfig.get().getBaseClaims(), true, false, false, true,
            plots, now, "", TownPerms.ALL_DENY
        );

        NeoTownsCache.putTown(town);
        ChunkOwnershipCache.setOwner(player.serverLevel(), chunk, townId.value());
        DatabaseManager.saveTown(town);

        int grant = NeoTownsConfig.get().getStartingGrantEmeralds();
        if (grant > 0) TreasuryManager.depositToTreasury(player.serverLevel(), chestPos, grant);

        NeoForge.EVENT_BUS.post(new TownFoundEvent(town, player));
        Messenger.success(player, "Town §b" + name + " §ffounded! Use §e/town claim§f to expand.");
        return 1;
    }

    // ── Disband ─────────────────────────────────────────────────────────────

    private static int executeDisband(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;

        DatabaseManager.deleteTown(town.id().value());
        NeoTownsCache.removeTown(town.id().value());
        NeoForge.EVENT_BUS.post(new TownDisbandEvent(town, player));
        Messenger.success(player, "Town §b" + town.name() + " §fhas been disbanded.");
        return 1;
    }

    // ── Claim ───────────────────────────────────────────────────────────────

    private static int executeClaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return claimChunk(ctx, false);
    }

    private static int executeClaimOutpost(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return claimChunk(ctx, true);
    }

    private static int claimChunk(CommandContext<CommandSourceStack> ctx, boolean outpost) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ChunkPos chunk = new ChunkPos(player.blockPosition());
        if (ChunkOwnershipCache.getOwner(player.serverLevel(), chunk) != null) {
            Messenger.error(player, "This chunk is already claimed.");
            return 1;
        }
        if (town.claimCount() >= maxClaims(town)) {
            Messenger.error(player, "Your town has reached its max claim limit.");
            return 1;
        }

        int cost = outpost
            ? NeoTownsConfig.get().getChunkClaimCost() * NeoTownsConfig.get().getOutpostClaimMultiplier()
            : NeoTownsConfig.get().getChunkClaimCost();

        if (!outpost) {
            Set<ChunkPos> existing = new HashSet<>();
            for (PlotData p : town.plots().values()) existing.add(p.pos());
            if (!existing.isEmpty() && !isAdjacent(chunk, existing)) {
                Messenger.error(player, "Not adjacent. Use §e/town claim outpost§f for non-adjacent.");
                return 1;
            }
        }

        if (!TownCommandHelper.deductFromInventory(player, cost)) {
            Messenger.error(player, "You need " + cost + " emeralds.");
            return 1;
        }

        var updatedPlots = new HashMap<>(town.plots());
        updatedPlots.put(chunk.toString(), new PlotData(chunk, player.serverLevel().dimension(), town.id(),
            outpost ? PlotType.OUTPOST : PlotType.DEFAULT, null, 0, false));
        TownDataBuilder builder = new TownDataBuilder(town);
        builder.plots = updatedPlots;
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        ChunkOwnershipCache.setOwner(player.serverLevel(), chunk, town.id().value());
        DatabaseManager.saveTown(updated);
        NeoForge.EVENT_BUS.post(new ClaimEvent(updated, chunk, true));
        Messenger.success(player, "Chunk claimed for §b" + town.name() + "§f.");
        return 1;
    }

    // ── Unclaim ─────────────────────────────────────────────────────────────

    private static int executeUnclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ChunkPos chunk = new ChunkPos(player.blockPosition());
        UUID owner = ChunkOwnershipCache.getOwner(player.serverLevel(), chunk);
        if (owner == null || !owner.equals(town.id().value())) {
            Messenger.error(player, "This chunk is not claimed by your town.");
            return 1;
        }
        if (town.plots().size() <= 1) {
            Messenger.error(player, "Cannot unclaim home chunk. Disband the town instead.");
            return 1;
        }

        var updatedPlots = new HashMap<>(town.plots());
        updatedPlots.remove(chunk.toString());
        TownDataBuilder builder = new TownDataBuilder(town);
        builder.plots = updatedPlots;
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        ChunkOwnershipCache.clearOwner(player.serverLevel(), chunk);
        DatabaseManager.saveTown(updated);
        NeoForge.EVENT_BUS.post(new ClaimEvent(updated, chunk, false));
        Messenger.success(player, "Chunk unclaimed.");
        return 1;
    }

    // ── Invite ──────────────────────────────────────────────────────────────

    private static int executeInvite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (target.getUUID().equals(player.getUUID())) {
            Messenger.error(player, "You cannot invite yourself.");
            return 1;
        }
        if (NeoTownsCache.getTownByPlayer(target.getUUID()) != null) {
            Messenger.error(player, "That player is already in a town.");
            return 1;
        }

        pendingInvites.put(target.getUUID(), new PendingInvite(town.id().value(), System.currentTimeMillis() + 120_000));
        Messenger.success(player, "Invited §b" + target.getName().getString() + "§f.");
        Messenger.info(target, "You are invited to §b" + town.name() + "§f. Use §e/town join " + town.name() + "§f.");
        return 1;
    }

    // ── Join ────────────────────────────────────────────────────────────────

    private static int executeJoin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (NeoTownsCache.getTownByPlayer(player.getUUID()) != null) {
            Messenger.error(player, "You are already in a town.");
            return 1;
        }

        String name = StringArgumentType.getString(ctx, "name");
        TownData target = null;
        for (TownData t : NeoTownsCache.allTowns()) {
            if (t.name().equalsIgnoreCase(name)) { target = t; break; }
        }
        if (target == null) {
            Messenger.error(player, "No town found with that name.");
            return 1;
        }

        PendingInvite invite = pendingInvites.get(player.getUUID());
        boolean hasInvite = invite != null && invite.townId().equals(target.id().value()) && System.currentTimeMillis() < invite.expiresAt();
        if (!target.isOpen() && !hasInvite) {
            Messenger.error(player, "That town is not open. You need an invitation.");
            return 1;
        }
        pendingInvites.remove(player.getUUID());

        var updatedResidents = new HashSet<>(target.residentUUIDs());
        updatedResidents.add(player.getUUID());
        TownDataBuilder builder = new TownDataBuilder(target);
        builder.residentUUIDs = Collections.unmodifiableSet(updatedResidents);
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        DatabaseManager.saveTown(updated);
        NeoForge.EVENT_BUS.post(new TownJoinEvent(updated, player));
        Messenger.success(player, "You joined §b" + target.name() + "§f.");
        return 1;
    }

    // ── Leave ───────────────────────────────────────────────────────────────

    private static int executeLeave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null) return 1;
        if (town.isMayor(player.getUUID())) {
            Messenger.error(player, "The mayor cannot leave. Transfer mayorhood first.");
            return 1;
        }

        var updatedResidents = new HashSet<>(town.residentUUIDs());
        updatedResidents.remove(player.getUUID());
        TownDataBuilder builder = new TownDataBuilder(town);
        builder.residentUUIDs = Collections.unmodifiableSet(updatedResidents);
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        DatabaseManager.saveTown(updated);
        NeoForge.EVENT_BUS.post(new TownLeaveEvent(updated, player));
        Messenger.success(player, "You left §b" + town.name() + "§f.");
        return 1;
    }

    // ── Kick ────────────────────────────────────────────────────────────────

    private static int executeKick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireAssistantOrMayor(player, town)) return 1;

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (target.getUUID().equals(player.getUUID())) {
            Messenger.error(player, "You cannot kick yourself.");
            return 1;
        }
        if (!town.isResident(target.getUUID())) {
            Messenger.error(player, "That player is not in your town.");
            return 1;
        }
        if (town.isMayor(target.getUUID())) {
            Messenger.error(player, "You cannot kick the mayor.");
            return 1;
        }

        var updatedResidents = new HashSet<>(town.residentUUIDs());
        var updatedAssistants = new HashSet<>(town.assistantUUIDs());
        updatedResidents.remove(target.getUUID());
        updatedAssistants.remove(target.getUUID());
        TownDataBuilder builder = new TownDataBuilder(town);
        builder.residentUUIDs = Collections.unmodifiableSet(updatedResidents);
        builder.assistantUUIDs = Collections.unmodifiableSet(updatedAssistants);
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Kicked §b" + target.getName().getString() + "§f.");
        Messenger.warn(target, "You were kicked from §b" + town.name() + "§f.");
        return 1;
    }

    // ── Deposit ─────────────────────────────────────────────────────────────

    private static int executeDeposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null) return 1;

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (!TownCommandHelper.deductFromInventory(player, amount)) {
            Messenger.error(player, "You don't have " + amount + " emeralds.");
            return 1;
        }
        if (!TreasuryManager.depositToTreasury(player.serverLevel(), town.treasuryChestPos(), amount)) {
            player.addItem(new ItemStack(Items.EMERALD, amount));
            Messenger.error(player, "Treasury chest is full or missing.");
            return 1;
        }

        long balance = TreasuryManager.scanTreasury(player.serverLevel(), town.treasuryChestPos());
        TownDataBuilder builder = new TownDataBuilder(town);
        builder.emeraldBalance = balance;
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Deposited " + amount + " ✦. Balance: §2" + balance + " ✦");
        return 1;
    }

    // ── Withdraw ────────────────────────────────────────────────────────────

    private static int executeWithdraw(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null || !TownCommandHelper.requireMayor(player, town)) return 1;

        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (!TreasuryManager.deductFromTreasury(player.serverLevel(), town.treasuryChestPos(), amount)) {
            Messenger.error(player, "Treasury doesn't have " + amount + " emeralds.");
            return 1;
        }
        player.addItem(new ItemStack(Items.EMERALD, amount));
        long balance = TreasuryManager.scanTreasury(player.serverLevel(), town.treasuryChestPos());
        TownDataBuilder builder = new TownDataBuilder(town);
        builder.emeraldBalance = balance;
        TownData updated = builder.build();
        NeoTownsCache.putTown(updated);
        DatabaseManager.saveTown(updated);
        Messenger.success(player, "Withdrew " + amount + " ✦. Balance: §2" + balance + " ✦");
        return 1;
    }

    // ── Balance ─────────────────────────────────────────────────────────────

    private static int executeBalance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null) return 1;
        long balance = TreasuryManager.scanTreasury(player.serverLevel(), town.treasuryChestPos());
        Messenger.info(player, "Treasury: §2" + balance + " ✦");
        return 1;
    }

    // ── Spawn ───────────────────────────────────────────────────────────────

    private static int executeSpawn(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        TownData town;
        if (name != null) {
            town = null;
            for (TownData t : NeoTownsCache.allTowns()) {
                if (t.name().equalsIgnoreCase(name)) { town = t; break; }
            }
            if (town == null) {
                Messenger.error(player, "No town found with that name.");
                return 1;
            }
        } else {
            town = NeoTownsCache.getTownByPlayer(player.getUUID());
            if (town == null) {
                Messenger.error(player, "You are not in a town. Use §e/town spawn <name>§f.");
                return 1;
            }
        }

        long cooldown = spawnCooldowns.getOrDefault(player.getUUID(), 0L);
        if (System.currentTimeMillis() < cooldown) {
            Messenger.error(player, "Please wait before teleporting again.");
            return 1;
        }

        var homePlot = town.plots().values().stream().findFirst().orElse(null);
        if (homePlot == null) {
            Messenger.error(player, "Town has no home location.");
            return 1;
        }

        BlockPos homePos = homePlot.pos().getWorldPosition();
        player.serverLevel().getChunkSource().getChunk(homePlot.pos().x, homePlot.pos().z, true);
        player.teleportTo(player.serverLevel(), homePos.getX() + 0.5, player.serverLevel().getHeight(), homePos.getZ() + 0.5, player.getYRot(), player.getXRot());
        spawnCooldowns.put(player.getUUID(), System.currentTimeMillis() + NeoTownsConfig.get().getSpawnCooldownSeconds() * 1000L);
        Messenger.success(player, "Teleported to §b" + town.name() + "§f.");
        return 1;
    }

    // ── Map ─────────────────────────────────────────────────────────────────

    private static int executeMap(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town = TownCommandHelper.requireTown(player);
        if (town == null) return 1;

        ChunkPos center = new ChunkPos(player.blockPosition());
        int radius = 4;
        player.sendSystemMessage(Component.literal("§6── " + town.name() + " Claims ──"));

        for (int dz = -radius; dz <= radius; dz++) {
            StringBuilder line = new StringBuilder();
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                boolean isMine = town.plots().containsKey(cp.toString());
                boolean here = dx == 0 && dz == 0;
                if (isMine) {
                    line.append(here ? "§aⓧ§r" : "§a█§r");
                } else {
                    line.append(here ? "§7Ⓧ§r" : "§7·§r");
                }
            }
            player.sendSystemMessage(Component.literal(line.toString()));
        }
        player.sendSystemMessage(Component.literal("§a█ §7= §fClaimed  §7Ⓧ §7= §fYou"));
        return 1;
    }

    // ── Info ────────────────────────────────────────────────────────────────

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownData town;
        if (name != null) {
            town = null;
            for (TownData t : NeoTownsCache.allTowns()) {
                if (t.name().equalsIgnoreCase(name)) { town = t; break; }
            }
            if (town == null) {
                Messenger.error(player, "No town found.");
                return 1;
            }
        } else {
            town = NeoTownsCache.getTownByPlayer(player.getUUID());
            if (town == null) {
                Messenger.error(player, "Specify a name: §e/town info <name>");
                return 1;
            }
        }
        String mayorName = ctx.getSource().getServer().getPlayerList().getPlayer(town.mayorUUID()) != null
            ? ctx.getSource().getServer().getPlayerList().getPlayer(town.mayorUUID()).getName().getString()
            : town.mayorUUID().toString().substring(0, 8);
        player.sendSystemMessage(Component.literal("§6═══ §e" + town.name() + " §6═══"));
        player.sendSystemMessage(Component.literal("§7Mayor: §f" + mayorName));
        player.sendSystemMessage(Component.literal("§7Residents: §f" + town.residentUUIDs().size()));
        player.sendSystemMessage(Component.literal("§7Claims: §f" + town.claimCount()));
        if (town.motd() != null && !town.motd().isEmpty())
            player.sendSystemMessage(Component.literal("§7MOTD: §f" + town.motd()));
        if (town.hasState()) {
            String sn = NeoTownsCache.getStateName(town.stateId().value());
            if (sn != null) player.sendSystemMessage(Component.literal("§7State: §f" + sn));
        }
        player.sendSystemMessage(Component.literal("§6══════════════"));
        return 1;
    }

    // ── List ────────────────────────────────────────────────────────────────

    private static int executeList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var all = NeoTownsCache.allTowns();
        if (all.isEmpty()) {
            Messenger.info(player, "No towns have been founded yet.");
            return 1;
        }
        player.sendSystemMessage(Component.literal("§6── Towns (" + all.size() + ") ──"));
        for (TownData t : all.stream().sorted(Comparator.comparing(t2 -> t2.name().toLowerCase())).toList()) {
            player.sendSystemMessage(Component.literal(
                "§e" + t.name() + " §7- §f" + t.residentUUIDs().size() + " residents, "
                + t.claimCount() + " claims" + (t.isOpen() ? " §a[Open]" : "")));
        }
        return 1;
    }
}
