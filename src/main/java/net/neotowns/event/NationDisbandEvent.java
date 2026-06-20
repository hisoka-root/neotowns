package net.neotowns.event;

import net.neotowns.model.NationData;
import net.neoforged.bus.api.Event;

public class NationDisbandEvent extends Event {
    private final NationData nation;

    public NationDisbandEvent(NationData nation) { this.nation = nation; }
    public NationData getNation() { return nation; }
}
