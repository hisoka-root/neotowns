package net.neotowns.data;

import net.neotowns.model.NationData;
import net.neotowns.model.StateData;
import net.neotowns.model.TownData;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class NeoTownsCache {

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Map<UUID, TownData> towns = new HashMap<>();
    private static final Map<UUID, StateData> states = new HashMap<>();
    private static final Map<UUID, NationData> nations = new HashMap<>();
    private static final Map<UUID, UUID> playerToTown = new HashMap<>();

    private NeoTownsCache() {}

    public static void loadAllFromDatabase() {
        lock.writeLock().lock();
        try {
            towns.clear();
            states.clear();
            nations.clear();
            playerToTown.clear();

            for (TownData town : DatabaseManager.loadAllTowns()) {
                towns.put(town.id().value(), town);
                town.residentUUIDs().forEach(r -> playerToTown.put(r, town.id().value()));
            }
            for (StateData state : DatabaseManager.loadAllStates()) {
                states.put(state.id().value(), state);
            }
            for (NationData nation : DatabaseManager.loadAllNations()) {
                nations.put(nation.id().value(), nation);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ── Towns ───────────────────────────────────────────────────────────────

    public static TownData getTown(UUID id) {
        lock.readLock().lock();
        try { return towns.get(id); }
        finally { lock.readLock().unlock(); }
    }

    public static void putTown(TownData town) {
        lock.writeLock().lock();
        try {
            towns.put(town.id().value(), town);
            town.residentUUIDs().forEach(r -> playerToTown.put(r, town.id().value()));
        } finally { lock.writeLock().unlock(); }
    }

    public static TownData removeTown(UUID id) {
        lock.writeLock().lock();
        try {
            TownData removed = towns.remove(id);
            if (removed != null) {
                removed.residentUUIDs().forEach(playerToTown::remove);
            }
            return removed;
        } finally { lock.writeLock().unlock(); }
    }

    public static TownData getTownByPlayer(UUID playerUUID) {
        lock.readLock().lock();
        try {
            UUID townId = playerToTown.get(playerUUID);
            return townId != null ? towns.get(townId) : null;
        } finally { lock.readLock().unlock(); }
    }

    public static String getTownName(UUID id) {
        TownData town = getTown(id);
        return town != null ? town.name() : "Wilderness";
    }

    public static Collection<TownData> allTowns() {
        lock.readLock().lock();
        try { return List.copyOf(towns.values()); }
        finally { lock.readLock().unlock(); }
    }

    // ── States ──────────────────────────────────────────────────────────────

    public static StateData getState(UUID id) {
        lock.readLock().lock();
        try { return states.get(id); }
        finally { lock.readLock().unlock(); }
    }

    public static void putState(StateData state) {
        lock.writeLock().lock();
        try { states.put(state.id().value(), state); }
        finally { lock.writeLock().unlock(); }
    }

    public static StateData removeState(UUID id) {
        lock.writeLock().lock();
        try { return states.remove(id); }
        finally { lock.writeLock().unlock(); }
    }

    public static String getStateName(UUID id) {
        StateData state = getState(id);
        return state != null ? state.name() : null;
    }

    public static Collection<StateData> allStates() {
        lock.readLock().lock();
        try { return List.copyOf(states.values()); }
        finally { lock.readLock().unlock(); }
    }

    // ── Nations ─────────────────────────────────────────────────────────────

    public static NationData getNation(UUID id) {
        lock.readLock().lock();
        try { return nations.get(id); }
        finally { lock.readLock().unlock(); }
    }

    public static void putNation(NationData nation) {
        lock.writeLock().lock();
        try { nations.put(nation.id().value(), nation); }
        finally { lock.writeLock().unlock(); }
    }

    public static NationData removeNation(UUID id) {
        lock.writeLock().lock();
        try { return nations.remove(id); }
        finally { lock.writeLock().unlock(); }
    }

    public static String getNationName(UUID id) {
        NationData nation = getNation(id);
        return nation != null ? nation.name() : null;
    }

    public static Collection<NationData> allNations() {
        lock.readLock().lock();
        try { return List.copyOf(nations.values()); }
        finally { lock.readLock().unlock(); }
    }
}
