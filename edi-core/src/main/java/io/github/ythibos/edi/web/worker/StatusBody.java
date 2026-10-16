package io.github.ythibos.edi.web.worker;

import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.required;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;

record StatusBody(String state, String key, String until, String text) {

    StatusState statusState() {
        return StatusState.named(required(state, "state"));
    }

    StatusKey statusKey() {
        return optional(key, StatusKey::new);
    }

    Instant untilInstant() {
        return optional(until, StatusBody::instant);
    }

    private static Instant instant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException notAnInstant) {
            throw new IllegalArgumentException("A wait lasts until an ISO-8601 instant such as 2026-10-16T14:30:00Z, not " + text + ".", notAnInstant);
        }
    }
}
