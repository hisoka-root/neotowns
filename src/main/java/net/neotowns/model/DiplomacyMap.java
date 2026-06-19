package net.neotowns.model;

import net.neotowns.model.enums.DiplomacyStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record DiplomacyMap(Map<NTId, DiplomacyStatus> relations) {

    public static DiplomacyMap empty() {
        return new DiplomacyMap(new HashMap<>());
    }

    public DiplomacyMap {
        relations = Collections.unmodifiableMap(new HashMap<>(relations));
    }

    public DiplomacyStatus getStatus(NTId nationId) {
        return relations.getOrDefault(nationId, DiplomacyStatus.NEUTRAL);
    }

    public DiplomacyMap withRelation(NTId nationId, DiplomacyStatus status) {
        Map<NTId, DiplomacyStatus> updated = new HashMap<>(relations);
        updated.put(nationId, status);
        return new DiplomacyMap(updated);
    }

    public DiplomacyMap withoutRelation(NTId nationId) {
        Map<NTId, DiplomacyStatus> updated = new HashMap<>(relations);
        updated.remove(nationId);
        return new DiplomacyMap(updated);
    }
}
