package net.neotowns.event;

import net.neoforged.bus.api.Event;

public class LawEnactedEvent extends Event {
    private final Object entity;
    private final String lawName;
    private final String lawText;

    public LawEnactedEvent(Object entity, String lawName, String lawText) {
        this.entity = entity;
        this.lawName = lawName;
        this.lawText = lawText;
    }

    public Object getEntity() { return entity; }
    public String getLawName() { return lawName; }
    public String getLawText() { return lawText; }
}
