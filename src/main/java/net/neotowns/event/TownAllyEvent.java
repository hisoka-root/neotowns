package net.neotowns.event;

import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

public class TownAllyEvent extends Event {
    private final TownData town;
    private final TownData ally;
    private final boolean adding;

    public TownAllyEvent(TownData town, TownData ally, boolean adding) {
        this.town = town;
        this.ally = ally;
        this.adding = adding;
    }

    public TownData getTown() { return town; }
    public TownData getAlly() { return ally; }
    public boolean isAdding() { return adding; }
}
