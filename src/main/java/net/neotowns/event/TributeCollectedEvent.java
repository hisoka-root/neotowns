package net.neotowns.event;

import net.neotowns.model.NationData;
import net.neoforged.bus.api.Event;

public class TributeCollectedEvent extends Event {
    private final NationData suzerain;
    private final NationData vassal;
    private final long amount;

    public TributeCollectedEvent(NationData suzerain, NationData vassal, long amount) {
        this.suzerain = suzerain;
        this.vassal = vassal;
        this.amount = amount;
    }

    public NationData getSuzerain() { return suzerain; }
    public NationData getVassal() { return vassal; }
    public long getAmount() { return amount; }
}
