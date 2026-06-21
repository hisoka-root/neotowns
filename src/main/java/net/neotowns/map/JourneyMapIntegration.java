package net.neotowns.map;

import journeymap.api.server.ServerAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.overlay.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neotowns.data.NeoTownsCache;
import net.neotowns.model.NationData;
import net.neotowns.model.StateData;
import net.neotowns.model.TownData;

import java.util.*;

public class JourneyMapIntegration implements MapIntegration {

    private final IServerAPI api;
    private final IServerOverlayAPI overlay;
    private boolean available;
    private MinecraftServer server;

    public JourneyMapIntegration() {
        IServerAPI a = null;
        IServerOverlayAPI o = null;
        try {
            a = ServerAPI.INSTANCE;
            o = a.getOverlayApi();
        } catch (Exception e) {}
        this.api = a;
        this.overlay = o;
        this.available = a != null && o != null;
    }

    @Override public String modId() { return "journeymap"; }

    @Override
    public void onChunkClaimed(TownData town, ChunkPos chunk, ServerLevel level) {
        if (!available) return;
        sendChunkOverlay(town, chunk, level);
        pushTownWaypoint(town);
    }

    @Override
    public void onChunkUnclaimed(TownData town, ChunkPos chunk, ServerLevel level) {
        if (!available) return;
        removeChunkOverlay(town, chunk, level);
    }

    @Override
    public void onTownFounded(TownData town) {
        if (!available) return;
        pushTownWaypoint(town);
        town.plots().forEach((key, plot) -> {
            var l = server != null ? server.getLevel(plot.world()) : null;
            if (l instanceof ServerLevel sl) sendChunkOverlay(town, plot.pos(), sl);
        });
    }

    @Override
    public void onTownDisbanded(TownData town) {
        if (!available || server == null) return;
        town.residentUUIDs().stream()
            .map(id -> server.getPlayerList().getPlayer(id))
            .filter(Objects::nonNull)
            .forEach(p -> api.deletePlayerWaypoint(p.getUUID(), town.name()));
    }

    @Override
    public void onTownRenamed(TownData town, String oldName) {}

    @Override
    public void onStateUpdated(StateData state) {
        if (!available) return;
        state.townIds().forEach(tid -> {
            var t = NeoTownsCache.getTown(tid.value());
            if (t == null) return;
            t.plots().forEach((k, plot) -> {
                var l = server != null ? server.getLevel(plot.world()) : null;
                if (l instanceof ServerLevel sl) sendChunkOverlay(t, plot.pos(), sl);
            });
        });
    }

    @Override
    public void onNationUpdated(NationData nation) {
        if (!available) return;
        nation.stateIds().forEach(sid -> {
            var s = NeoTownsCache.getState(sid.value());
            if (s != null) onStateUpdated(s);
        });
    }

    @Override
    public void drawTownHomeMarker(TownData town) { pushTownWaypoint(town); }
    @Override
    public void drawStateCapitalMarker(StateData state) {
        state.townIds().stream().findFirst().ifPresent(tid -> {
            var t = NeoTownsCache.getTown(tid.value());
            if (t != null) pushTownWaypoint(t);
        });
    }
    @Override
    public void drawNationCapitalMarker(NationData nation) {}
    @Override
    public void removeMarker(String markerId) {}

    @Override
    public void rebuildAll(MinecraftServer server) {
        this.server = server;
        if (!available) return;
        NeoTownsCache.allTowns().forEach(town -> {
            pushTownWaypoint(town);
            town.plots().forEach((k, plot) -> {
                var l = server.getLevel(plot.world());
                if (l instanceof ServerLevel sl) sendChunkOverlay(town, plot.pos(), sl);
            });
        });
    }

    @Override
    public void shutdown() {}

    private void pushTownWaypoint(TownData town) {
        if (!available) return;
        var homePlot = town.plots().values().stream().findFirst().orElse(null);
        if (homePlot == null) return;
        Waypoint wp = WaypointFactory.createWaypoint(town.name(), homePlot.pos().getWorldPosition(), "NeoTowns", homePlot.world(), true);
        town.residentUUIDs().forEach(uuid -> api.addPlayerWaypoint(uuid, wp));
    }

    private void sendChunkOverlay(TownData town, ChunkPos chunk, ServerLevel level) {
        int color = resolveColor(town);
        long x1 = chunk.getMinBlockX(), z1 = chunk.getMinBlockZ();
        long x2 = chunk.getMaxBlockX() + 1L, z2 = chunk.getMaxBlockZ() + 1L;

        OverlayPoints outer = new OverlayPoints(List.of(x1, z1, x2, z1, x2, z2, x1, z2));
        OverlayPolygon polygon = new OverlayPolygon(outer, List.of());
        OverlayShapeProps props = new OverlayShapeProps(color, 0.35f, color, 2.0f, 1.0f, 0, 0, Integer.MAX_VALUE, Set.of(), Set.of(), town.name(), "");
        String overlayId = "nt_" + town.id().value() + "_" + chunk.x + "_" + chunk.z;
        ServerPolygon sp = new ServerPolygon(overlayId, level.dimension(), List.of(polygon), props);

        town.residentUUIDs().stream()
            .map(id -> level.getServer().getPlayerList().getPlayer(id))
            .filter(Objects::nonNull)
            .forEach(player -> overlay.show(player, "NeoTowns", sp));
    }

    private void removeChunkOverlay(TownData town, ChunkPos chunk, ServerLevel level) {
        String overlayId = "nt_" + town.id().value() + "_" + chunk.x + "_" + chunk.z;
        town.residentUUIDs().stream()
            .map(id -> level.getServer().getPlayerList().getPlayer(id))
            .filter(Objects::nonNull)
            .forEach(player -> overlay.remove(player, "NeoTowns", overlayId));
    }

    private int resolveColor(TownData town) {
        int color = town.id().value().hashCode() & 0xFFFFFF;
        if (town.stateId() != null) {
            var s = NeoTownsCache.getState(town.stateId().value());
            if (s != null) {
                color = s.id().value().hashCode() & 0xFFFFFF;
                if (s.nationId() != null) {
                    var n = NeoTownsCache.getNation(s.nationId().value());
                    if (n != null) color = n.id().value().hashCode() & 0xFFFFFF;
                }
            }
        }
        return color;
    }
}
