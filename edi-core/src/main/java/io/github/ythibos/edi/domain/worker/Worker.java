package io.github.ythibos.edi.domain.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.status.StatusReport;

public record Worker(
        WorkerId id,
        UUID session,
        CategoryId category,
        Route route,
        WorkerWindow window,
        WorkerActivity activity,
        Instant launchedAt,
        Instant startedAt,
        Instant briefedAt,
        Instant launchFailedAt,
        Instant retiredAt,
        ReportedStatus lastStatus,
        DoneCheck doneCheck) {

    public Worker {
        Objects.requireNonNull(id, "A worker has an id.");
        Objects.requireNonNull(session, () -> id + " has the Claude session id EDI chose for it.");
        Objects.requireNonNull(route, () -> id + " runs on an explicit model.");
        Objects.requireNonNull(window, () -> id + " lives in a recorded tmux window.");
        Objects.requireNonNull(launchedAt, () -> id + " records when it was launched.");
    }

    public static Worker launched(WorkerId id, UUID session, CategoryId category, Route route, WorkerWindow window, Instant at) {
        return new Worker(id, session, category, route, window, WorkerActivity.STARTING, at, null, null, null, null, null, null);
    }

    public Worker started(Instant at) {
        return new Worker(id, session, category, route, window, WorkerActivity.IDLE, launchedAt,
                Objects.requireNonNullElse(startedAt, at), briefedAt, launchFailedAt, retiredAt, lastStatus, doneCheck);
    }

    public Worker promptSubmitted(Instant at) {
        return new Worker(id, session, category, route, window, WorkerActivity.BUSY, launchedAt,
                Objects.requireNonNullElse(startedAt, at), Objects.requireNonNullElse(briefedAt, at), launchFailedAt, retiredAt, lastStatus, doneCheck);
    }

    public Worker stopped() {
        return new Worker(id, session, category, route, window, WorkerActivity.IDLE, launchedAt,
                startedAt, briefedAt, launchFailedAt, retiredAt, lastStatus, doneCheck);
    }

    public Worker reported(StatusReport report, Instant at) {
        refuseWhenRetired();
        return new Worker(id, session, category, route, window, activity, launchedAt,
                startedAt, briefedAt, launchFailedAt, retiredAt, new ReportedStatus(report, at), doneCheckFor(report));
    }

    public Worker doneChecked(boolean accepted) {
        if (doneCheck != DoneCheck.CHECKING) {
            throw new Refusal(id + " has no done waiting for its check.");
        }
        return new Worker(id, session, category, route, window, activity, launchedAt,
                startedAt, briefedAt, launchFailedAt, retiredAt, lastStatus, accepted ? DoneCheck.ACCEPTED : DoneCheck.REFUSED);
    }

    public Worker launchFailed(Instant at) {
        return new Worker(id, session, category, route, window, activity, launchedAt, startedAt, briefedAt, at, retiredAt, lastStatus, doneCheck);
    }

    public Worker retired(Instant at) {
        refuseWhenRetired();
        return new Worker(id, session, category, route, window, activity, launchedAt, startedAt, briefedAt, launchFailedAt, at, lastStatus, doneCheck);
    }

    public boolean isRetired() {
        return retiredAt != null;
    }

    public boolean isIdle() {
        return activity == WorkerActivity.IDLE;
    }

    public boolean awaitsItsDoneCheck() {
        return doneCheck == DoneCheck.CHECKING && !isRetired();
    }

    public boolean countsAgainstCapacity() {
        return !isRetired() && doneCheck != DoneCheck.ACCEPTED;
    }

    public boolean missedItsLaunch(Instant now, Duration patience) {
        boolean noHookYet = startedAt == null && briefedAt == null;
        return noHookYet && launchFailedAt == null && !isRetired() && !now.isBefore(launchedAt.plus(patience));
    }

    private DoneCheck doneCheckFor(StatusReport report) {
        if (!report.isDone()) {
            return null;
        }
        return category.isMechanical() ? DoneCheck.CHECKING : DoneCheck.ACCEPTED;
    }

    private void refuseWhenRetired() {
        if (isRetired()) {
            throw new Refusal(id + " was retired; its task has another worker now.");
        }
    }

    public record ReportedStatus(StatusReport report, Instant at) {
    }
}
