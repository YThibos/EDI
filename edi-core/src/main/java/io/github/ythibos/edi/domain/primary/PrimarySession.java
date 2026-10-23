package io.github.ythibos.edi.domain.primary;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.worker.UsageLimit;

public record PrimarySession(
        UUID id,
        Instant startedAt,
        SessionProcess process,
        Instant claimedAt,
        PrimaryTurn turn,
        Instant typedAt,
        String notification,
        Instant notifiedAt,
        UsageLimit limit,
        Instant limitReachedAt,
        Instant endedAt,
        Instant sitrepOpenedAt) {

    public PrimarySession {
        Objects.requireNonNull(id, "A primary session is named by its Claude Code session id.");
        Objects.requireNonNull(startedAt, () -> "Session " + id + " records when it started.");
    }

    public static PrimarySession started(UUID id, SessionProcess process, Instant at) {
        return new PrimarySession(id, at, process, null, null, null, null, null, null, null, null, null);
    }

    public PrimarySession restarted(SessionProcess restartedProcess) {
        return new PrimarySession(id, startedAt, restartedProcess == null ? process : restartedProcess, claimedAt, turn, typedAt,
                notification, notifiedAt, limit, limitReachedAt, null, null);
    }

    public PrimarySession claimed(Instant at) {
        return new PrimarySession(id, startedAt, process, at, turn, typedAt,
                notification, notifiedAt, limit, limitReachedAt, endedAt, sitrepOpenedAt);
    }

    public PrimarySession relinquished() {
        return new PrimarySession(id, startedAt, process, null, turn, typedAt,
                notification, notifiedAt, limit, limitReachedAt, endedAt, sitrepOpenedAt);
    }

    public PrimarySession promptSubmitted(Prompt prompt, long eventMark, Instant at) {
        return new PrimarySession(id, startedAt, process, claimedAt, PrimaryTurn.startedAt(at, eventMark),
                prompt.isTypedByTheCaptain() ? at : typedAt, notification, notifiedAt, limit, limitReachedAt, endedAt,
                sitrepOpenedForTheOpeningTurn());
    }

    public PrimarySession reported(Instant at) {
        return turn == null ? this : withTurn(turn.reported(at));
    }

    public PrimarySession stopBlocked() {
        return withTurn(turn.stopBlocked());
    }

    public PrimarySession turnEnded() {
        return new PrimarySession(id, startedAt, process, claimedAt, turn, typedAt, notification, notifiedAt, null, null, endedAt, null);
    }

    public PrimarySession notified(String kind, Instant at) {
        return new PrimarySession(id, startedAt, process, claimedAt, turn, typedAt, kind, at, limit, limitReachedAt, endedAt, sitrepOpenedAt);
    }

    public PrimarySession stoppedOn(UsageLimit reached, Instant at) {
        return new PrimarySession(id, startedAt, process, claimedAt, turn, typedAt, notification, notifiedAt, reached, at, endedAt, sitrepOpenedAt);
    }

    public PrimarySession ended(Instant at) {
        return new PrimarySession(id, startedAt, process, claimedAt, turn, typedAt,
                notification, notifiedAt, limit, limitReachedAt, at, sitrepOpenedAt);
    }

    public PrimarySession sitrepOpened(Instant at) {
        return new PrimarySession(id, startedAt, process, claimedAt, turn, typedAt, notification, notifiedAt, limit, limitReachedAt, endedAt, at);
    }

    public boolean isEnded() {
        return endedAt != null;
    }

    public boolean hasClaimedTheRole() {
        return claimedAt != null;
    }

    public boolean showsTheSitrepItsStartOpened() {
        return sitrepOpenedAt != null;
    }

    private Instant sitrepOpenedForTheOpeningTurn() {
        return turn == null ? sitrepOpenedAt : null;
    }

    private PrimarySession withTurn(PrimaryTurn newTurn) {
        return new PrimarySession(id, startedAt, process, claimedAt, newTurn, typedAt,
                notification, notifiedAt, limit, limitReachedAt, endedAt, sitrepOpenedAt);
    }
}
