package net.neotowns;

import com.mojang.logging.LogUtils;
import net.neotowns.command.NeoTownsCommand;
import net.neotowns.config.NeoTownsConfig;
import net.neotowns.data.ChunkOwnershipCache;
import net.neotowns.data.DatabaseManager;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.handler.ServerLifecycleHandler;
import net.neotowns.permissions.PermissionsIntegrationRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;

@Mod("neotowns")
public class NeoTownsMod {

    public static final String MODID = "neotowns";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoTownsMod(IEventBus modBus) {
        if (FMLEnvironment.dist.isClient()) {
            LOGGER.info("[NeoTowns] Running on client side — no server logic loaded.");
            return;
        }

        LOGGER.info("[NeoTowns] Initializing server-side mod...");

        // Config
        NeoTownsConfig config = NeoTownsConfig.load();

        // Database
        Path dbPath = FMLPaths.CONFIGDIR.get().getParent().resolve(config.getDbPath());
        DatabaseManager.init(
            config.getDbType(), dbPath,
            config.getDbHost(), config.getDbName(),
            config.getDbUser(), config.getDbPass(),
            config.getDbPoolSize()
        );

        // Cache — load all data from DB into memory
        NeoTownsCache.loadAllFromDatabase();
        ChunkOwnershipCache.loadAll();

        // Permissions integration
        PermissionsIntegrationRegistry.init(config.getPreferredIntegration());

        // Commands
        NeoTownsCommand.register();

        LOGGER.info("[NeoTowns] Initialization complete.");
    }
}
