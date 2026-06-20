package net.neotowns.event;

import net.neotowns.model.StateData;
import net.neoforged.bus.api.Event;

public class StateDisbandEvent extends Event {
    private final StateData state;

    public StateDisbandEvent(StateData state) {
        this.state = state;
    }

    public StateData getState() { return state; }
}
