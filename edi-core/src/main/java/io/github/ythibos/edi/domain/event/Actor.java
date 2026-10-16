package io.github.ythibos.edi.domain.event;

public record Actor(String name) {

    public static final Actor PRIMARY = new Actor("primary");

    public Actor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Every event names who caused it.");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
