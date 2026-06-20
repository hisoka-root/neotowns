package net.neotowns.event;

import net.neotowns.model.NationData;
import net.neotowns.model.enums.DiplomacyStatus;
import net.neoforged.bus.api.Event;

public class DiplomacyChangeEvent extends Event {
    private final NationData a;
    private final NationData b;
    private final DiplomacyStatus oldStatus;
    private final DiplomacyStatus newStatus;

    public DiplomacyChangeEvent(NationData a, NationData b, DiplomacyStatus oldStatus, DiplomacyStatus newStatus) {
        this.a = a;
        this.b = b;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public NationData getA() { return a; }
    public NationData getB() { return b; }
    public DiplomacyStatus getOldStatus() { return oldStatus; }
    public DiplomacyStatus getNewStatus() { return newStatus; }
}
