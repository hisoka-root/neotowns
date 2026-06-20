package net.neotowns.event;

import net.neotowns.model.StateData;
import net.neoforged.bus.api.Event;

public class StateSessionResultEvent extends Event {
    private final StateData state;
    private final String topic;
    private final boolean passed;

    public StateSessionResultEvent(StateData state, String topic, boolean passed) {
        this.state = state;
        this.topic = topic;
        this.passed = passed;
    }

    public StateData getState() { return state; }
    public String getTopic() { return topic; }
    public boolean isPassed() { return passed; }
}
