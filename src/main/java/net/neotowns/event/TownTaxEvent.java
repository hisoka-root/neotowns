package net.neotowns.event;

import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

public class TownTaxEvent extends Event {
    private final TownData town;
    private long taxAmount;

    public TownTaxEvent(TownData town, long taxAmount) {
        this.town = town;
        this.taxAmount = taxAmount;
    }

    public TownData getTown() { return town; }
    public long getTaxAmount() { return taxAmount; }
    public void setTaxAmount(long taxAmount) { this.taxAmount = taxAmount; }
}
