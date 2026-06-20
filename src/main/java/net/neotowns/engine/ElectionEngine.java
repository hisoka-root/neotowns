package net.neotowns.engine;

import net.neotowns.model.StateData;
import net.neotowns.model.enums.GovernmentType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ElectionEngine {

    private ElectionEngine() {}

    public record Election(String entityType, UUID entityId, UUID[] candidates, long closesAt) {}

    private static final Map<UUID, Election> activeElections = new ConcurrentHashMap<>();

    public static void startElection(String entityType, UUID entityId, List<UUID> candidates, long durationMs) {
        activeElections.put(entityId, new Election(
            entityType, entityId, candidates.toArray(UUID[]::new),
            System.currentTimeMillis() + durationMs
        ));
    }

    public static Election getElection(UUID entityId) {
        return activeElections.get(entityId);
    }

    public static boolean isElectionActive(UUID entityId) {
        Election e = activeElections.get(entityId);
        return e != null && System.currentTimeMillis() < e.closesAt();
    }

    public static UUID tally(UUID entityId) {
        Election e = activeElections.remove(entityId);
        if (e == null) return null;
        if (e.candidates().length == 0) return null;

        ElectionResult result = results.computeIfAbsent(entityId, k -> new ElectionResult());
        Map<UUID, Integer> votes = result.votes;

        UUID winner = null;
        int maxVotes = -1;
        for (Map.Entry<UUID, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                winner = entry.getKey();
            }
        }

        if (winner == null && e.candidates().length > 0) {
            winner = e.candidates()[0];
        }

        results.remove(entityId);
        return winner;
    }

    private static final Map<UUID, ElectionResult> results = new ConcurrentHashMap<>();
    public record ElectionResult(Map<UUID, Integer> votes) {
        public ElectionResult() { this(new HashMap<>()); }
    }

    public static void castVote(UUID entityId, UUID voterId, UUID candidateId) {
        Election e = activeElections.get(entityId);
        if (e == null || System.currentTimeMillis() >= e.closesAt()) return;
        results.computeIfAbsent(entityId, k -> new ElectionResult())
            .votes.put(voterId, results.get(entityId).votes().getOrDefault(voterId, 0) + 1);
    }

    public static boolean isEligibleVoter(StateData state, UUID voterId, GovernmentType govType) {
        return switch (govType) {
            case DEMOCRACY -> state.cabinet().containsKey(voterId) || state.townIds().stream()
                .anyMatch(tid -> {
                    var town = net.neotowns.data.NeoTownsCache.getTown(tid.value());
                    return town != null && town.isResident(voterId);
                });
            case OLIGARCHY -> state.cabinet().containsKey(voterId);
            case REPUBLIC -> state.cabinet().containsKey(voterId);
            case COUNCIL -> state.townIds().stream()
                .anyMatch(tid -> {
                    var town = net.neotowns.data.NeoTownsCache.getTown(tid.value());
                    return town != null && town.isMayor(voterId);
                });
            default -> false;
        };
    }
}
