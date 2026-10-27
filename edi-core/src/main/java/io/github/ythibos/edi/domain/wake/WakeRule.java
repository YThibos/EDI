package io.github.ythibos.edi.domain.wake;

import java.util.Optional;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswerRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardArmed;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardRetired;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardRoundPosted;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.CaptainSaid;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeAppliedToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeNotApplied;
import io.github.ythibos.edi.domain.event.DomainEvent.CleanupRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.CloneRefreshed;
import io.github.ythibos.edi.domain.event.DomainEvent.ContextLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.event.DomainEvent.DeclaredWaitEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.DeferredLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.DeliveryRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.DispatchDeferred;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftOpened;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.HeavyRunsAtOnce;
import io.github.ythibos.edi.domain.event.DomainEvent.HoldDateArrived;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeHeldBack;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeTyped;
import io.github.ythibos.edi.domain.event.DomainEvent.LostInOutage;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeAccepted;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.PipelineRunFinished;
import io.github.ythibos.edi.domain.event.DomainEvent.PipelineRunSeen;
import io.github.ythibos.edi.domain.event.DomainEvent.RecheckAsked;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryChosen;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryCostRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.ResearchQueuedFirst;
import io.github.ythibos.edi.domain.event.DomainEvent.ReviewCounted;
import io.github.ythibos.edi.domain.event.DomainEvent.ScoutPromoted;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerAcknowledged;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerNotReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdopted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCostRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDropped;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskImported;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskKeyed;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketAddedToSprint;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketChoiceRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketCreated;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketLinked;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketTransitioned;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketUpdateRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.ToolCallRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkDiscarded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerBusyTooLong;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerDied;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerNotified;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerSessionEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStale;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStarted;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopMissed;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopRequested;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerTurnEnded;
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
            case WorkerLaunchFailed failed -> wake(WakeKind.LAUNCH_FAILED, failed, failed.worker(), failed.pane().word());
            case DeferredLaunchFailed failed -> wake(WakeKind.DEFERRED_LAUNCH_FAILED, failed, null, failed.because());
            case UsageLimitReached reached -> Optional.of(new Wake(WakeKind.USAGE_LIMIT, reached.task(), reached.worker(),
                    reached.limit().name(), reached.limit().resetsAt()));
            case ContextLimitReached reached -> Optional.of(new Wake(WakeKind.CONTEXT_FULL, reached.task(), reached.worker(), "", reached.fullSince()));
            case WorkerStopMissed missed -> Optional.of(new Wake(stopMissedKindOf(missed), missed.task(), missed.worker(),
                    String.valueOf(missed.message()), missed.stoppedAt()));
            case LimitResumeHeldBack heldBack -> wake(WakeKind.LIMIT_RESUME_HELD_BACK, heldBack, heldBack.worker(), heldBack.pane().word());
            case WorkerDied died -> wake(WakeKind.WORKER_DIED, died, died.worker(), died.cause().sentence());
            case LostInOutage lost -> wake(WakeKind.LOST_IN_OUTAGE, lost, lost.worker(), lost.number() + ": " + lost.cause().sentence());
            case WorkerStale stale -> Optional.of(new Wake(WakeKind.WORKER_STALE, stale.task(), stale.worker(), "", stale.quietSince()));
            case WorkerBusyTooLong busy -> Optional.of(new Wake(WakeKind.BUSY_TOO_LONG, busy.task(), busy.worker(), "", busy.busySince()));
            case HeavyRunsAtOnce atOnce -> wake(WakeKind.HEAVY_RUNS_AT_ONCE, atOnce, atOnce.worker(), runsOf(atOnce));
            case DeclaredWaitEnded ended -> Optional.of(new Wake(WakeKind.WAIT_ENDED, ended.task(), ended.worker(),
                    ended.pause().text(), ended.pause().until()));
            case HoldDateArrived due -> wake(WakeKind.HOLD_DUE, due, null, due.until().toString());
            case DraftMerged merged -> wake(WakeKind.DRAFT_MERGED, merged, null, merged.address().url());
            case DraftClosed closed -> wake(WakeKind.DRAFT_CLOSED, closed, null, closed.address().url());
            case BranchDeliveryFailed failed -> wake(WakeKind.DELIVERY_FAILED, failed, null, failed.because());
            case ChangeNotApplied notApplied -> wake(WakeKind.DELIVERY_FAILED, notApplied, null, notApplied.because());
            case CleanupRefused refused -> wake(WakeKind.CLEANUP_REFUSED, refused, null, String.join("; ", refused.findings()));
            case DecisionRaised raised -> wake(WakeKind.DECISION_RAISED, raised, null, raised.number() + " " + raised.card().title());
            case TicketUpdateRefused refused -> wake(WakeKind.TICKET_REFUSED, refused, null,
                    refused.update() + " of " + refused.key() + ": " + refused.because());
            case TaskKeyed keyed -> wake(WakeKind.TASK_KEYED, keyed, null, keyed.key().toString());
            case DecisionAnswered answered -> wake(WakeKind.DECISION_ANSWERED, answered, null,
                    answered.number() + " in the " + answered.answer().channel().word() + ": " + answered.answer().verbatim());
            case RecheckAsked asked -> wake(WakeKind.RECHECK_ASKED, asked, null, recheckOf(asked));
            case BoardAnswered answered -> wake(WakeKind.BOARD_ANSWERED, answered, null, roundOf(answered));
            case BoardAnswerRefused refused -> wake(WakeKind.BOARD_ANSWER_REFUSED, refused, null, refused.number() + ": " + refused.because());
            case TaskAdded _, TaskNoted _, TaskHeld _, TaskReleased _, TaskDone _, DispatchDeferred _,
                    TaskDispatched _, WorkerStarted _, WorkerRetired _, SteerSent _, SteerReceived _, SteerAcknowledged _, SteerNotReceived _,
                    ToolCallRefused _, MechanicalChangeAccepted _, MechanicalChangeRefused _, BranchDeliveredToCaptain _, WorkerRelaunched _,
                    WorkerNotified _, WorkerSessionEnded _, WorkerTurnEnded _, WorkerStoppedOnAnError _, LimitResumeTyped _, RecoveryChosen _, RecoveryFailed _,
                    WorkerStopRequested _, RecoveryCostRecorded _, DeliveryRefused _, DraftRecorded _, DraftOpened _, CloneRefreshed _,
                    ChangeAppliedToCaptain _, TaskCleanedUp _, TaskCostRecorded _, PipelineRunSeen _, PipelineRunFinished _,
                    TicketCreated _, TicketChoiceRefused _, TicketAddedToSprint _, TicketLinked _, TicketTransitioned _, ResearchQueuedFirst _,
                    ReviewCounted _, BoardArmed _, BoardRoundPosted _, BoardClosed _, BoardRetired _, CaptainSaid _, ScoutPromoted _, TaskDropped _,
                    WorkDiscarded _, TaskImported _, TaskAdopted _ -> Optional.empty();
        };
    }

    private static WakeKind stopMissedKindOf(WorkerStopMissed missed) {
        return missed.noticeTyped() ? WakeKind.STOP_MISSED : WakeKind.STOP_MISSED_NOTICE_WAITING;
    }

    private static String runsOf(HeavyRunsAtOnce atOnce) {
        return "a " + atOnce.run().words() + " while " + atOnce.beside().stream()
                .map(other -> other.worker() + " runs a " + other.run().words())
                .collect(Collectors.joining(" and "));
    }

    private static String recheckOf(RecheckAsked asked) {
        return asked.note().isEmpty() ? asked.number().toString() : asked.number() + " (\"" + asked.note() + "\")";
    }

    private static String roundOf(BoardAnswered answered) {
        String ending = answered.sessionEnded() ? ", and the captain ended the session" : "";
        return "board " + answered.board() + " round " + answered.round() + ": " + answered.prompts() + " prompt" + (answered.prompts() == 1 ? "" : "s")
                + ending;
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
