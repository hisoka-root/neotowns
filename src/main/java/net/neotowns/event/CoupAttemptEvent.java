package net.neotowns.event;

import net.neoforged.bus.api.Event;

import java.util.UUID;

public class CoupAttemptEvent extends Event {
    private final Object entity;
    private final UUID instigatorId;

    public CoupAttemptEvent(Object entity, UUID instigatorId) {
        this.entity = entity;
        this.instigatorId = instigatorId;
    }

    public Object getEntity() { return entity; }
    public UUID getInstigatorId() { return instigatorId; }
}
