package net.neotowns.event;

import net.neotowns.model.NationData;
import net.neoforged.bus.api.Event;

public class NationFoundEvent extends Event {
    private final NationData nation;

    public NationFoundEvent(NationData nation) { this.nation = nation; }
    public NationData getNation() { return nation; }
}
