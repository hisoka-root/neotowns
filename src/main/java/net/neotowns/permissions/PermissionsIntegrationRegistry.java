package net.neotowns.permissions;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

public final class PermissionsIntegrationRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static PermissionsIntegration active = new OpLevelPermissionsIntegration();

    private PermissionsIntegrationRegistry() {}

    public static void init(String preferred) {
        if ("none".equalsIgnoreCase(preferred)) {
            LOGGER.info("[NeoTowns] Permissions integration disabled by config.");
            active = new OpLevelPermissionsIntegration();
            return;
        }

        boolean luckPermsLoaded = ModList.get().isLoaded("luckperms");

        if ("luckperms".equalsIgnoreCase(preferred) && luckPermsLoaded) {
            active = new LuckPermsIntegration();
            LOGGER.info("[NeoTowns] Permissions: LuckPerms (config-preferred)");
        } else if (luckPermsLoaded) {
            active = new LuckPermsIntegration();
            LOGGER.info("[NeoTowns] Permissions: LuckPerms (auto-detected)");
        } else {
            LOGGER.info("[NeoTowns] Permissions: no mod found, using OP-level fallback");
            active = new OpLevelPermissionsIntegration();
        }
    }

    public static PermissionsIntegration get() {
        return active;
    }
}
