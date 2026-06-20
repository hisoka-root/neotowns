package net.neotowns.event;

import net.neotowns.model.StateData;
import net.neotowns.model.TownData;
import net.neoforged.bus.api.Event;

public class StateMembershipEvent extends Event {
    private final StateData state;
    private final TownData town;
    private final boolean joining;

    public StateMembershipEvent(StateData state, TownData town, boolean joining) {
        this.state = state;
        this.town = town;
        this.joining = joining;
    }

    public StateData getState() { return state; }
    public TownData getTown() { return town; }
    public boolean isJoining() { return joining; }
}
