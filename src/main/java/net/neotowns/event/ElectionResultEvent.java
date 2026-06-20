package net.neotowns.event;

import net.neoforged.bus.api.Event;

import java.util.Map;
import java.util.UUID;

public class ElectionResultEvent extends Event {
    private final Object entity;
    private final UUID winnerId;
    private final Map<UUID, Integer> votes;

    public ElectionResultEvent(Object entity, UUID winnerId, Map<UUID, Integer> votes) {
        this.entity = entity;
        this.winnerId = winnerId;
        this.votes = votes;
    }

    public Object getEntity() { return entity; }
    public UUID getWinnerId() { return winnerId; }
    public Map<UUID, Integer> getVotes() { return votes; }
}
