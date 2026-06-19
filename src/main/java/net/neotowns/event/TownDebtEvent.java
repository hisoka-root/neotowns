package net.neotowns.event;

import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

public class TownDebtEvent extends Event {
    private final TownData town;
    private final int daysInDebt;

    public TownDebtEvent(TownData town, int daysInDebt) {
        this.town = town;
        this.daysInDebt = daysInDebt;
    }

    public TownData getTown() { return town; }
    public int getDaysInDebt() { return daysInDebt; }
}
