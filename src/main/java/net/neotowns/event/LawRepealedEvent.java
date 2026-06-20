package net.neotowns.event;

import net.neoforged.bus.api.Event;

public class LawRepealedEvent extends Event {
    private final Object entity;
    private final String lawName;

    public LawRepealedEvent(Object entity, String lawName) {
        this.entity = entity;
        this.lawName = lawName;
    }

    public Object getEntity() { return entity; }
    public String getLawName() { return lawName; }
}
