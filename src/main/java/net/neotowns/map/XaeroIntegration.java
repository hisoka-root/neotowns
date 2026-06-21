package net.neotowns.map;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neotowns.model.NationData;
import net.neotowns.model.StateData;
import net.neotowns.model.TownData;

import java.util.Objects;

public class XaeroIntegration implements MapIntegration {

    private MinecraftServer server;

    public XaeroIntegration() {}

    @Override public String modId() { return "xaerominimap"; }

    @Override
    public void onChunkClaimed(TownData town, ChunkPos chunk, ServerLevel level) {
        pushWaypoint(town);
    }

    @Override
    public void onChunkUnclaimed(TownData town, ChunkPos chunk, ServerLevel level) {}

    @Override
    public void onTownFounded(TownData town) {
        pushWaypoint(town);
    }

    @Override
    public void onTownDisbanded(TownData town) {}
    @Override
    public void onTownRenamed(TownData town, String oldName) {}
    @Override
    public void onStateUpdated(StateData state) {}
    @Override
    public void onNationUpdated(NationData nation) {}
    @Override
    public void drawTownHomeMarker(TownData town) { pushWaypoint(town); }
    @Override
    public void drawStateCapitalMarker(StateData state) {}
    @Override
    public void drawNationCapitalMarker(NationData nation) {}
    @Override
    public void removeMarker(String markerId) {}

    @Override
    public void rebuildAll(MinecraftServer server) { this.server = server; }
    @Override
    public void shutdown() {}

    private void pushWaypoint(TownData town) {
        if (server == null) return;
        var homePlot = town.plots().values().stream().findFirst().orElse(null);
        if (homePlot == null) return;
        var pos = homePlot.pos().getWorldPosition();
        String dim = homePlot.world().location().toString();
        int color = town.id().value().hashCode() & 0xFFFFFF;

        String wpData = town.name() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ()
            + ":" + color + ":" + dim + ":neotowns";
        String payload = "§1§1§1§1§1§1Waypoint§r§r§r§r§r§r" + wpData;
        var packet = new ClientboundSystemChatPacket(Component.literal(payload), false);

        town.residentUUIDs().stream()
            .map(id -> server.getPlayerList().getPlayer(id))
            .filter(Objects::nonNull)
            .forEach(player -> player.connection.send(packet));
    }
}
