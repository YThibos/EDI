package io.github.ythibos.edi.domain.tracker;

import java.util.Set;

import io.github.ythibos.edi.domain.project.WorkKey;

public record RepositorySightings(Set<WorkKey> landedOnTheBase, Sighting reachedProduction, Sighting underOpenRequest) {

    public RepositorySightings {
        landedOnTheBase = Set.copyOf(landedOnTheBase);
    }
}
