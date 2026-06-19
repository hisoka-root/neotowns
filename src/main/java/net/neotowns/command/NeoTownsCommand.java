package net.neotowns.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.DatabaseManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;

import static net.minecraft.commands.Commands.literal;

@EventBusSubscriber(modid = "neotowns")
public final class NeoTownsCommand {

    private NeoTownsCommand() {}

    public static void register() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            literal("neotowns")
                .then(literal("version")
                    .executes(NeoTownsCommand::executeVersion))
                .then(literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(NeoTownsCommand::executeReload))
                .then(literal("backup")
                    .requires(src -> src.hasPermission(2))
                    .executes(NeoTownsCommand::executeBackup))
        );
    }

    private static int executeVersion(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal("NeoTowns v0.0.1 — github.com/hisoka-root/neotowns"), false);
        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> ctx) {
        NeoTownsConfig.get().reload();
        ctx.getSource().sendSuccess(() ->
            Component.literal("Reloaded NeoTowns configuration."), true);
        return 1;
    }

    private static int executeBackup(CommandContext<CommandSourceStack> ctx) {
        Path backupDir = ctx.getSource().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("neotowns").resolve("backups");
        DatabaseManager.backup(backupDir);
        ctx.getSource().sendSuccess(() ->
            Component.literal("NeoTowns backup saved to " + backupDir), true);
        return 1;
    }
}
