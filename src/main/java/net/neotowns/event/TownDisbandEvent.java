package net.neotowns.event;

import net.minecraft.server.level.ServerPlayer;
import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

import javax.annotation.Nullable;

public class TownDisbandEvent extends Event {
    private final TownData town;
    @Nullable private final ServerPlayer initiator;

    public TownDisbandEvent(TownData town, @Nullable ServerPlayer initiator) {
        this.town = town;
        this.initiator = initiator;
    }

    public TownData getTown() { return town; }
    @Nullable public ServerPlayer getInitiator() { return initiator; }
    public boolean isAutoDisband() { return initiator == null; }
}
