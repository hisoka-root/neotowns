package net.neotowns.event;

import net.neotowns.model.NationData;
import net.neoforged.bus.api.Event;

public class WarDeclaredEvent extends Event {
    private final NationData aggressor;
    private final NationData defender;

    public WarDeclaredEvent(NationData aggressor, NationData defender) {
        this.aggressor = aggressor;
        this.defender = defender;
    }

    public NationData getAggressor() { return aggressor; }
    public NationData getDefender() { return defender; }
}
