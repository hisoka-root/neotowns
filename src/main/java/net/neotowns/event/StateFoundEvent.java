package net.neotowns.event;

import net.neotowns.model.StateData;
import net.neoforged.bus.api.Event;

public class StateFoundEvent extends Event {
    private final StateData state;

    public StateFoundEvent(StateData state) {
        this.state = state;
    }

    public StateData getState() { return state; }
}
