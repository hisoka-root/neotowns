package net.neotowns.event;

import net.neoforged.bus.api.Event;

import java.util.List;
import java.util.UUID;

public class ElectionStartEvent extends Event {
    private final Object entity;
    private final List<UUID> candidates;

    public ElectionStartEvent(Object entity, List<UUID> candidates) {
        this.entity = entity;
        this.candidates = candidates;
    }

    public Object getEntity() { return entity; }
    public List<UUID> getCandidates() { return candidates; }
}
