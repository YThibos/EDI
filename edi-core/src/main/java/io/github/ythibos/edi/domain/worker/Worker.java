package io.github.ythibos.edi.domain.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;

public record Worker(
        WorkerId id,
        UUID session,
        CategoryId category,
        Route route,
        WorkerWindow window,
        WorkerLaunch launch,
        WorkerActivity activity,
        Instant activitySince,
        Instant startedAt,
        Instant briefedAt,
        String transcript,
        Instant endedAt,
        Instant stopRequestedAt,
        Instant launchFailedAt,
        Instant retiredAt,
        ReportedStatus lastStatus,
        DoneCheck doneCheck,
        LimitPark limitPark) {

    public Worker {
        Objects.requireNonNull(id, "A worker has an id.");
        Objects.requireNonNull(session, () -> id + " has the Claude session id EDI chose for it.");
        Objects.requireNonNull(route, () -> id + " runs on an explicit model.");
        Objects.requireNonNull(window, () -> id + " lives in a recorded tmux window.");
        Objects.requireNonNull(launch, () -> id + " records how and when it was launched.");
        Objects.requireNonNull(activity, () -> id + " is starting, busy or idle.");
        Objects.requireNonNull(activitySince, () -> id + " records since when it is " + activity.word() + ".");
    }

    public static Worker launched(WorkerId id, UUID session, CategoryId category, Route route, WorkerWindow window, WorkerLaunch launch) {
        return new Worker(id, session, category, route, window, launch, WorkerActivity.STARTING, launch.at(),
                null, null, null, null, null, null, null, null, null, null);
    }

    public Instant launchedAt() {
        return launch.at();
    }

    public Worker started(UUID reportedSession, String reportedTranscript, Instant at) {
        boolean firstHook = activity == WorkerActivity.STARTING;
        return new Worker(id, reportedSession == null ? session : reportedSession, category, route, window, launch,
                firstHook ? WorkerActivity.IDLE : activity, firstHook ? at : activitySince,
                Objects.requireNonNullElse(startedAt, at), briefedAt, reportedTranscript == null ? transcript : reportedTranscript,
                endedAt, stopRequestedAt, launchFailedAt, retiredAt, lastStatus, doneCheck, limitPark);
    }

    public Worker promptSubmitted(Instant at) {
        return new Worker(id, session, category, route, window, launch, WorkerActivity.BUSY, at,
                Objects.requireNonNullElse(startedAt, at), Objects.requireNonNullElse(briefedAt, at), transcript,
                endedAt, stopRequestedAt, launchFailedAt, retiredAt, lastStatus, doneCheck, null);
    }

    public Worker turnEnded(Instant at) {
        return idleSince(at, null, endedAt);
    }

    public Worker parkedOn(UsageLimit limit, Instant at) {
        return idleSince(at, new LimitPark(limit, at), endedAt);
    }

    public Worker sessionEnded(Instant at) {
        return idleSince(at, limitPark, at);
    }

    public Worker stopRequested(Instant at) {
        refuseWhenRetired();
        return new Worker(id, session, category, route, window, launch, activity, activitySince, startedAt, briefedAt,
                transcript, endedAt, at, launchFailedAt, retiredAt, lastStatus, doneCheck, limitPark);
    }

    public Worker reported(StatusReport report, Instant at) {
        refuseWhenRetired();
        return new Worker(id, session, category, route, window, launch, activity, activitySince, startedAt, briefedAt,
                transcript, endedAt, stopRequestedAt, launchFailedAt, retiredAt, new ReportedStatus(report, at), doneCheckFor(report), limitPark);
    }

    public Worker doneChecked(boolean accepted) {
        if (doneCheck != DoneCheck.CHECKING) {
            throw new Refusal(id + " has no done waiting for its check.");
        }
        return new Worker(id, session, category, route, window, launch, activity, activitySince, startedAt, briefedAt,
                transcript, endedAt, stopRequestedAt, launchFailedAt, retiredAt, lastStatus, accepted ? DoneCheck.ACCEPTED : DoneCheck.REFUSED, limitPark);
    }

    public Worker launchFailed(Instant at) {
        return new Worker(id, session, category, route, window, launch, activity, activitySince, startedAt, briefedAt,
                transcript, endedAt, stopRequestedAt, at, retiredAt, lastStatus, doneCheck, limitPark);
    }

    public Worker retired(Instant at) {
        refuseWhenRetired();
        return new Worker(id, session, category, route, window, launch, activity, activitySince, startedAt, briefedAt,
                transcript, endedAt, stopRequestedAt, launchFailedAt, at, lastStatus, doneCheck, limitPark);
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

    public boolean handedItsWorkOver() {
        return doneCheck == DoneCheck.ACCEPTED || doneCheck == DoneCheck.REFUSED;
    }

    public boolean missedItsLaunch(Instant now, Duration patience) {
        boolean noHookYet = startedAt == null && briefedAt == null;
        return noHookYet && launchFailedAt == null && !isRetired() && !now.isBefore(launchedAt().plus(patience));
    }

    public boolean lastReported(StatusState state) {
        return lastStatus != null && lastStatus.report().state() == state;
    }

    public Instant quietSince() {
        return lastStatus == null || lastStatus.at().isBefore(activitySince) ? activitySince : lastStatus.at();
    }

    private Worker idleSince(Instant at, LimitPark park, Instant ended) {
        boolean changes = activity != WorkerActivity.IDLE;
        return new Worker(id, session, category, route, window, launch, WorkerActivity.IDLE, changes ? at : activitySince,
                startedAt, briefedAt, transcript, ended, stopRequestedAt, launchFailedAt, retiredAt, lastStatus, doneCheck, park);
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
