package io.github.ythibos.edi.web.tracker;

import io.github.ythibos.edi.domain.project.WorkKey;

record WalkRequest(String key, String state) {

    WorkKey workKey() {
        if (key == null) {
            throw new IllegalArgumentException("A walk moves the ticket a work key names.");
        }
        return WorkKey.parse(key);
    }

    String target() {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("A walk goes to the state a ticket is to reach.");
        }
        return state.strip();
    }
}
