package net.neotowns.model;

import java.util.UUID;

public record NTId(UUID value) {
    public static NTId random() {
        return new NTId(UUID.randomUUID());
    }

    public static NTId fromString(String s) {
        return new NTId(UUID.fromString(s));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
