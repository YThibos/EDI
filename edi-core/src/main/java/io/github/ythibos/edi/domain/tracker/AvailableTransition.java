package io.github.ythibos.edi.domain.tracker;

import java.util.Objects;

public record AvailableTransition(String id, String name, String to) {

    public AvailableTransition {
        Objects.requireNonNull(id, "A transition has the id the tracker takes.");
        name = Objects.requireNonNullElse(name, "").strip();
        to = Objects.requireNonNullElse(to, "").strip();
    }
}
