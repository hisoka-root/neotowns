package net.neotowns.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.level.ServerPlayer;

public class LuckPermsIntegration implements PermissionsIntegration {

    private final LuckPerms lp;

    public LuckPermsIntegration() {
        this.lp = LuckPermsProvider.get();
    }

    @Override
    public boolean hasPermission(ServerPlayer player, String node) {
        User user = lp.getUserManager().getUser(player.getUUID());
        if (user == null) return false;
        QueryOptions opts = lp.getContextManager().getQueryOptions(player);
        if (opts == null) {
            opts = QueryOptions.defaultContextualOptions();
        }
        return user.getCachedData()
            .getPermissionData(opts)
            .checkPermission(node)
            .asBoolean();
    }

    @Override
    public String modId() {
        return "luckperms";
    }
}
