package net.neotowns.model;

import net.neotowns.model.enums.PermFlag;

public record TownPerms(long bitmask) {

    public static final TownPerms ALL_DENY = new TownPerms(0L);

    public boolean has(PermFlag flag) {
        return (bitmask & (1L << flag.ordinal())) != 0;
    }

    public TownPerms with(PermFlag flag, boolean value) {
        long mask = 1L << flag.ordinal();
        return value
            ? new TownPerms(bitmask | mask)
            : new TownPerms(bitmask & ~mask);
    }

    public static TownPerms fromBitmask(long bitmask) {
        return new TownPerms(bitmask);
    }

    public long toBitmask() {
        return bitmask;
    }
}
