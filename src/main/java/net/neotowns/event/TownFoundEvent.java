package net.neotowns.event;

import net.minecraft.server.level.ServerPlayer;
import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

public class TownFoundEvent extends Event {
    private final TownData town;
    private final ServerPlayer founder;

    public TownFoundEvent(TownData town, ServerPlayer founder) {
        this.town = town;
        this.founder = founder;
    }

    public TownData getTown() { return town; }
    public ServerPlayer getFounder() { return founder; }
}
