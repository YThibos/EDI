package io.github.ythibos.edi.domain.wake;

import java.util.Optional;

import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.event.DomainEvent.DeclaredWaitEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.DeferredLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.DispatchDeferred;
import io.github.ythibos.edi.domain.event.DomainEvent.HoldDateArrived;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeHeldBack;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeTyped;
import io.github.ythibos.edi.domain.event.DomainEvent.LostInOutage;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeAccepted;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryChosen;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryCostRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerAcknowledged;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerNotReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.event.DomainEvent.ToolCallRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerBusyTooLong;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerDied;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerNotified;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerSessionEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStale;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStarted;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopRequested;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.worker.WorkerId;

public final class WakeRule {

    private WakeRule() {
    }

    public static Optional<Wake> wakeFor(DomainEvent event, Actor actor) {
        if (actor.equals(Actor.PRIMARY)) {
            return Optional.empty();
        }
        return switch (event) {
            case StatusReported reported -> statusWake(reported);
            case DecisionAnswered answered -> wake(WakeKind.DECISION_ANSWERED, answered, null, answered.number().toString());
            case SteerNotReceived unreceived -> wake(WakeKind.STEER_NOT_RECEIVED, unreceived, null, "message " + unreceived.number());
            case WorkerLaunchFailed failed -> wake(WakeKind.LAUNCH_FAILED, failed, failed.worker(), failed.pane().word());
            case DeferredLaunchFailed failed -> wake(WakeKind.DEFERRED_LAUNCH_FAILED, failed, null, failed.because());
            case MechanicalChangeAccepted accepted -> wake(WakeKind.MECHANICAL_ACCEPTED, accepted, accepted.worker(), "");
            case MechanicalChangeRefused refused -> wake(WakeKind.MECHANICAL_REFUSED, refused, refused.worker(), refused.because());
            case BranchDeliveredToCaptain delivered -> wake(WakeKind.BRANCH_DELIVERED, delivered, null, delivered.branch().toString());
            case BranchDeliveryFailed failed -> wake(WakeKind.BRANCH_DELIVERY_FAILED, failed, null, failed.branch() + ": " + failed.because());
            case UsageLimitReached reached -> Optional.of(new Wake(WakeKind.USAGE_LIMIT, reached.task(), reached.worker(),
                    reached.limit().name(), reached.limit().resetsAt()));
            case LimitResumeHeldBack heldBack -> wake(WakeKind.LIMIT_RESUME_HELD_BACK, heldBack, heldBack.worker(), heldBack.pane().word());
            case WorkerDied died -> wake(WakeKind.WORKER_DIED, died, died.worker(), died.cause().sentence());
            case LostInOutage lost -> wake(WakeKind.LOST_IN_OUTAGE, lost, lost.worker(), lost.number() + ": " + lost.cause().sentence());
            case RecoveryFailed failed -> wake(WakeKind.RECOVERY_FAILED, failed, null, failed.because());
            case WorkerStale stale -> Optional.of(new Wake(WakeKind.WORKER_STALE, stale.task(), stale.worker(), "", stale.quietSince()));
            case WorkerBusyTooLong busy -> Optional.of(new Wake(WakeKind.BUSY_TOO_LONG, busy.task(), busy.worker(), "", busy.busySince()));
            case DeclaredWaitEnded ended -> Optional.of(new Wake(WakeKind.WAIT_ENDED, ended.task(), ended.worker(),
                    ended.pause().text(), ended.pause().until()));
            case HoldDateArrived due -> wake(WakeKind.HOLD_DUE, due, null, due.until().toString());
            case TaskAdded _, TaskNoted _, TaskHeld _, TaskReleased _, DecisionRaised _, TaskDone _, DispatchDeferred _,
                    TaskDispatched _, WorkerStarted _, WorkerRetired _, SteerSent _, SteerReceived _, SteerAcknowledged _,
                    ToolCallRefused _, WorkerRelaunched _, WorkerNotified _, WorkerSessionEnded _, WorkerStoppedOnAnError _,
                    LimitResumeTyped _, RecoveryChosen _, WorkerStopRequested _, RecoveryCostRecorded _ -> Optional.empty();
        };
    }

    private static Optional<Wake> statusWake(StatusReported reported) {
        StatusReport report = reported.report();
        WakeKind kind = switch (report.state()) {
            case NEEDS_DECISION -> WakeKind.NEEDS_DECISION;
            case BLOCKED -> WakeKind.BLOCKED;
            case FAILED -> WakeKind.FAILED;
            case DONE -> WakeKind.DONE;
            case WORKING, PAUSED, RESOLVED -> null;
        };
        String key = report.key() == null ? "" : "(" + report.key() + ") ";
        return kind == null ? Optional.empty() : wake(kind, reported, reported.worker(), key + report.text());
    }

    private static Optional<Wake> wake(WakeKind kind, DomainEvent event, WorkerId worker, String detail) {
        return Optional.of(new Wake(kind, event.task(), worker, detail, null));
    }
}
