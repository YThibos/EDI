package io.github.ythibos.edi.domain.tracker;

import java.util.Set;

import io.github.ythibos.edi.domain.project.WorkKey;

public record Sighting(Set<WorkKey> keys, String unavailableBecause) {

    public Sighting {
        keys = Set.copyOf(keys);
    }

    public static Sighting of(Set<WorkKey> keys) {
        return new Sighting(keys, null);
    }

    public static Sighting unavailable(String because) {
        return new Sighting(Set.of(), because);
    }

    public boolean isAvailable() {
        return unavailableBecause == null;
    }

    public boolean includes(WorkKey key) {
        return keys.contains(key);
    }
}
