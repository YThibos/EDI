package io.github.ythibos.edi.domain.event;

import io.github.ythibos.edi.domain.worker.WorkerId;

public record Actor(String name) {

    public static final Actor CAPTAIN = new Actor("captain");
    public static final Actor PRIMARY = new Actor("primary");
    public static final Actor CORE = new Actor("core");

    public Actor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Every event names who caused it.");
        }
    }

    public static Actor worker(WorkerId worker) {
        return new Actor("worker:" + worker);
    }

    @Override
    public String toString() {
        return name;
    }
}
