package net.neotowns.event;

import net.neotowns.model.StateData;
import net.neoforged.bus.api.Event;

public class StateTaxEvent extends Event {
    private final StateData state;
    private long taxAmount;

    public StateTaxEvent(StateData state, long taxAmount) {
        this.state = state;
        this.taxAmount = taxAmount;
    }

    public StateData getState() { return state; }
    public long getTaxAmount() { return taxAmount; }
    public void setTaxAmount(long taxAmount) { this.taxAmount = taxAmount; }
}
