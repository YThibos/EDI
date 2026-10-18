package io.github.ythibos.edi.domain.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record Presence(PresenceMode mode, Instant since, PresenceCause cause, Mandate mandate, Instant demoUntil) {

    public Presence {
        Objects.requireNonNull(mode, "Presence has a mode.");
        Objects.requireNonNull(since, "Presence records since when it holds.");
        Objects.requireNonNull(cause, "Presence records what set it.");
        if ((mode == PresenceMode.AWAY) != (mandate != null)) {
            throw new IllegalArgumentException("Away mode, and only away mode, carries the captain's mandate.");
        }
        if (demoUntil != null && mode != PresenceMode.PRESENT) {
            throw new IllegalArgumentException("A demo holds present, so it ends whenever presence leaves present.");
        }
    }

    public static Presence presentSince(Instant at) {
        return new Presence(PresenceMode.PRESENT, at, PresenceCause.CAPTAIN, null, null);
    }

    public Presence captainTyped(Instant at) {
        return isPresent() ? this : new Presence(PresenceMode.PRESENT, at, PresenceCause.CAPTAIN, null, null);
    }

    public Presence present(Instant at) {
        return new Presence(PresenceMode.PRESENT, isPresent() ? since : at, PresenceCause.CAPTAIN, null, demoUntil);
    }

    public Presence quiet(Instant at) {
        return new Presence(PresenceMode.QUIET, at, PresenceCause.CAPTAIN, null, null);
    }

    public Presence away(Mandate given, Instant at) {
        return new Presence(PresenceMode.AWAY, at, PresenceCause.CAPTAIN, Objects.requireNonNull(given, "Away mode needs the captain's words."), null);
    }

    public Presence demo(Instant at, Instant until) {
        if (!until.isAfter(at)) {
            throw new IllegalArgumentException("A demo holds present for a while after it starts.");
        }
        return new Presence(PresenceMode.PRESENT, isPresent() ? since : at, PresenceCause.CAPTAIN, null, until);
    }

    public Presence demoOff() {
        return demoUntil == null ? this : new Presence(mode, since, cause, mandate, null);
    }

    public Presence quietAfterSilence(Instant lastSignOfTheCaptain, Duration threshold, Instant now) {
        Instant quietFrom = lastSignOfTheCaptain.plus(threshold);
        if (!isPresent() || demoHoldsAt(now) || now.isBefore(quietFrom)) {
            return this;
        }
        return new Presence(PresenceMode.QUIET, quietFrom.isAfter(since) ? quietFrom : now, PresenceCause.SILENCE, null, null);
    }

    public Presence quietForAMeeting(Instant meetingStart, Instant lastSignOfTheCaptain, Instant now) {
        if (!isPresent() || demoHoldsAt(now) || lastSignOfTheCaptain.isAfter(meetingStart)) {
            return this;
        }
        return new Presence(PresenceMode.QUIET, meetingStart.isAfter(since) ? meetingStart : now, PresenceCause.MEETING, null, null);
    }

    public Presence demoEndedBy(Instant now) {
        return demoUntil != null && !demoHoldsAt(now) ? demoOff() : this;
    }

    public boolean demoHoldsAt(Instant now) {
        return demoUntil != null && now.isBefore(demoUntil);
    }

    public boolean returnsFrom(Presence before) {
        return isPresent() && !before.isPresent();
    }

    public boolean isPresent() {
        return mode == PresenceMode.PRESENT;
    }

    public boolean keepsTheChatQuiet() {
        return !isPresent();
    }
}
