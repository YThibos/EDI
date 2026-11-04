package io.github.ythibos.edi.adapter.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.github.ythibos.edi.domain.tracker.TrackerKind;

@JsonIgnoreProperties(ignoreUnknown = true)
record RegisteredTracker(String kind, String projectKey) {

    TrackerKind trackerKind() {
        if (kind == null) {
            throw new IllegalArgumentException("A project's tracker names its kind: jira, edi, github or none.");
        }
        return TrackerKind.named(kind);
    }
}
