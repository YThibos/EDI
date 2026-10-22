package io.github.ythibos.edi.web.presence;

import java.time.Instant;

import io.github.ythibos.edi.domain.presence.Presence;

public record PresenceView(String mode, Instant since, String cause, String mandate, Integer workerCap, Instant demoUntil, String words) {

    static PresenceView of(Presence presence, PresenceWords words) {
        return new PresenceView(presence.mode().word(), presence.since(), presence.cause().word(),
                presence.mandate() == null ? null : presence.mandate().words(), presence.mandate() == null ? null : presence.mandate().workerCap(),
                presence.demoUntil(), words.of(presence));
    }
}
