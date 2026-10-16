package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;

public record CaptainOverride(Route route, String reason) {

    public CaptainOverride {
        Objects.requireNonNull(route, "The captain's override names the model to run on.");
        reason = Objects.requireNonNullElse(reason, "").strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("An override of the dispatch rules records the captain's reason for it.");
        }
    }
}
