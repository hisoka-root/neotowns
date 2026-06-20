package net.neotowns.event;

import net.neotowns.model.NationData;
import net.neotowns.model.StateData;
import net.neoforged.bus.api.Event;

public class NationMembershipEvent extends Event {
    private final NationData nation;
    private final StateData state;
    private final boolean joining;

    public NationMembershipEvent(NationData nation, StateData state, boolean joining) {
        this.nation = nation;
        this.state = state;
        this.joining = joining;
    }

    public NationData getNation() { return nation; }
    public StateData getState() { return state; }
    public boolean isJoining() { return joining; }
}
