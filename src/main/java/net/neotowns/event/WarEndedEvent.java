package net.neotowns.event;

import net.neotowns.model.NationData;
import net.neoforged.bus.api.Event;

public class WarEndedEvent extends Event {
    private final NationData winner;
    private final NationData loser;
    private final boolean wasSurrender;

    public WarEndedEvent(NationData winner, NationData loser, boolean wasSurrender) {
        this.winner = winner;
        this.loser = loser;
        this.wasSurrender = wasSurrender;
    }

    public NationData getWinner() { return winner; }
    public NationData getLoser() { return loser; }
    public boolean isWasSurrender() { return wasSurrender; }
}
