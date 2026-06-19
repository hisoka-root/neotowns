package net.neotowns.event;

import net.minecraft.server.level.ServerPlayer;
import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

public class TownLeaveEvent extends Event {
    private final TownData town;
    private final ServerPlayer player;

    public TownLeaveEvent(TownData town, ServerPlayer player) {
        this.town = town;
        this.player = player;
    }

    public TownData getTown() { return town; }
    public ServerPlayer getPlayer() { return player; }
}
