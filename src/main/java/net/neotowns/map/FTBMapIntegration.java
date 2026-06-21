package net.neotowns.map;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.model.NationData;
import net.neotowns.model.StateData;
import net.neotowns.model.TownData;

import java.util.*;

public class FTBMapIntegration implements MapIntegration {

    private MinecraftServer server;
    private boolean available;
    private final Map<UUID, UUID> townToTeam = new HashMap<>();

    @Override public String modId() { return "ftbchunks"; }

    @Override
    public void onChunkClaimed(TownData town, ChunkPos chunk, ServerLevel level) {
        if (!available) return;
        getTeamData(town).ifPresent(data -> {
            data.claim(server.createCommandSourceStack(), new ChunkDimPos(level.dimension(), chunk), false);
        });
    }

    @Override
    public void onChunkUnclaimed(TownData town, ChunkPos chunk, ServerLevel level) {
        if (!available) return;
        getTeamData(town).ifPresent(data -> {
            data.unclaim(server.createCommandSourceStack(), new ChunkDimPos(level.dimension(), chunk), false);
        });
    }

    @Override
    public void onTownFounded(TownData town) {
        ensureTeam(town);
    }

    @Override
    public void onTownDisbanded(TownData town) {
        var teamId = townToTeam.remove(town.id().value());
        if (teamId == null || server == null) return;
        var team = FTBTeamsAPI.api().getManager().getTeamByID(teamId).orElse(null);
        if (team == null) return;
        var data = FTBChunksAPI.api().getManager().getOrCreateData(team);
        if (data != null) {
            data.getClaimedChunks().forEach(c ->
                data.unclaim(server.createCommandSourceStack(), c.getPos(), false));
        }
    }

    @Override
    public void onTownRenamed(TownData town, String oldName) {
        var teamId = townToTeam.get(town.id().value());
        if (teamId != null) {
            var team = FTBTeamsAPI.api().getManager().getTeamByID(teamId).orElse(null);
            if (team != null) {
                team.setProperty(dev.ftb.mods.ftbteams.api.property.TeamProperties.DISPLAY_NAME, town.name());
            }
        }
    }

    @Override
    public void onStateUpdated(StateData state) {
        if (!available || server == null) return;
        state.townIds().forEach(tid -> {
            var town = NeoTownsCache.getTown(tid.value());
            if (town == null) return;
            var teamId = ensureTeam(town);
            if (teamId == null) return;
            var team = FTBTeamsAPI.api().getManager().getTeamByID(teamId).orElse(null);
            if (team == null) return;
            var description = state.nationId() != null
                ? "State: " + state.name()
                : state.name();
            team.setProperty(dev.ftb.mods.ftbteams.api.property.TeamProperties.DESCRIPTION, description);
            // Re-claim all chunks so they appear with the team's color on FTB Map
            var data = FTBChunksAPI.api().getManager().getOrCreateData(team);
            if (data == null) return;
            town.plots().forEach((key, plot) -> {
                var level = server.getLevel(plot.world());
                if (level instanceof ServerLevel sl) {
                    data.claim(server.createCommandSourceStack(),
                        new ChunkDimPos(sl.dimension(), plot.pos()), false);
                }
            });
        });
    }

    @Override
    public void onNationUpdated(NationData nation) {
        if (!available || server == null) return;
        nation.stateIds().forEach(sid -> {
            var state = NeoTownsCache.getState(sid.value());
            if (state != null) onStateUpdated(state);
        });
    }

    @Override
    public void drawTownHomeMarker(TownData town) {}
    @Override
    public void drawStateCapitalMarker(StateData state) {}
    @Override
    public void drawNationCapitalMarker(NationData nation) {}
    @Override
    public void removeMarker(String markerId) {}

    @Override
    public void rebuildAll(MinecraftServer server) {
        this.server = server;
        if (!available) return;
        NeoTownsCache.allTowns().forEach(town -> {
            var teamId = ensureTeam(town);
            if (teamId == null) return;
            var team = FTBTeamsAPI.api().getManager().getTeamByID(teamId).orElse(null);
            if (team == null) return;
            var data = FTBChunksAPI.api().getManager().getOrCreateData(team);
            if (data == null) return;
            town.plots().forEach((key, plot) -> {
                var level = server.getLevel(plot.world());
                if (level instanceof ServerLevel sl) {
                    data.claim(server.createCommandSourceStack(),
                        new ChunkDimPos(sl.dimension(), plot.pos()), false);
                }
            });
        });
    }

    @Override
    public void shutdown() {}

    private UUID ensureTeam(TownData town) {
        var existing = townToTeam.get(town.id().value());
        if (existing != null) return existing;
        var mayor = server != null ? server.getPlayerList().getPlayer(town.mayorUUID()) : null;
        if (mayor == null) return null;
        try {
            var mgr = FTBTeamsAPI.api().getManager();
            var team = mgr.createPartyTeam(mayor, town.name(), "", null);
            townToTeam.put(town.id().value(), team.getId());
            available = true;
            return team.getId();
        } catch (Exception e) {
            available = false;
            return null;
        }
    }

    private Optional<dev.ftb.mods.ftbchunks.api.ChunkTeamData> getTeamData(TownData town) {
        var teamId = townToTeam.get(town.id().value());
        if (teamId == null) {
            teamId = ensureTeam(town);
            if (teamId == null) return Optional.empty();
        }
        var team = FTBTeamsAPI.api().getManager().getTeamByID(teamId).orElse(null);
        if (team == null) return Optional.empty();
        return Optional.ofNullable(FTBChunksAPI.api().getManager().getOrCreateData(team));
    }
}
