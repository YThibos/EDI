package io.github.ythibos.edi.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.guard.GuardRule;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.recovery.RecoveryAction;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.StageChange;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.HeavyRun;
import io.github.ythibos.edi.domain.worker.HeavyRuns.WorkerRun;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.PaneState;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WorkerId;

public sealed interface DomainEvent {

    TaskId task();

    record TaskAdded(TaskId task, Intake intake) implements DomainEvent {
    }

    record TaskNoted(TaskId task, String note) implements DomainEvent {
    }

    record TaskHeld(TaskId task, CaptainHold hold) implements DomainEvent {
    }

    record TaskReleased(TaskId task) implements DomainEvent {
    }

    record DecisionRaised(TaskId task, DecisionNumber number, DecisionCard card) implements DomainEvent {
    }

    record DecisionAnswered(TaskId task, DecisionNumber number, CaptainAnswer answer) implements DomainEvent {
    }

    record DecisionRoundClosed(TaskId task, DecisionNumber number, String title, Instant opened) implements DomainEvent {
    }

    record TaskDone(TaskId task) implements DomainEvent {
    }

    record StageChanged(StageChange change) implements DomainEvent {

        @Override
        public TaskId task() {
            return change.task();
        }
    }

    record DispatchDeferred(TaskId task, ProjectName project, CategoryId category, int running, int capacity) implements DomainEvent {
    }

    record DeferredLaunchFailed(TaskId task, String because) implements DomainEvent {
    }

    record TaskDispatched(TaskId task, WorkerId worker, CategoryId category, Route route, BranchName branch, String because) implements DomainEvent {
    }

    record WorkerStarted(TaskId task, WorkerId worker) implements DomainEvent {
    }

    record WorkerLaunchFailed(TaskId task, WorkerId worker, PaneState pane) implements DomainEvent {
    }

    record WorkerRetired(TaskId task, WorkerId worker, String because) implements DomainEvent {
    }

    record StatusReported(TaskId task, WorkerId worker, StatusReport report) implements DomainEvent {
    }

    record SteerSent(TaskId task, int number, StatusKey resolves) implements DomainEvent {
    }

    record SteerReceived(TaskId task, int number, String proof) implements DomainEvent {
    }

    record SteerAcknowledged(TaskId task, int number) implements DomainEvent {
    }

    record SteerNotReceived(TaskId task, int number) implements DomainEvent {
    }

    record ToolCallRefused(TaskId task, WorkerId worker, GuardRule rule) implements DomainEvent {
    }

    record MechanicalChangeAccepted(TaskId task, WorkerId worker) implements DomainEvent {
    }

    record MechanicalChangeRefused(TaskId task, WorkerId worker, String because) implements DomainEvent {
    }

    record BranchDeliveredToCaptain(TaskId task, BranchName branch) implements DomainEvent {
    }

    record BranchDeliveryFailed(TaskId task, BranchName branch, String because) implements DomainEvent {
    }

    record WorkerRelaunched(TaskId task, WorkerId worker, LaunchKind launch, Route route, String because) implements DomainEvent {
    }

    record WorkerNotified(TaskId task, WorkerId worker, String kind) implements DomainEvent {
    }

    record WorkerSessionEnded(TaskId task, WorkerId worker, String reason) implements DomainEvent {
    }

    record WorkerTurnEnded(TaskId task, WorkerId worker) implements DomainEvent {
    }

    record WorkerStoppedOnAnError(TaskId task, WorkerId worker, String error) implements DomainEvent {
    }

    record UsageLimitReached(TaskId task, WorkerId worker, UsageLimit limit) implements DomainEvent {
    }

    record ContextLimitReached(TaskId task, WorkerId worker, Instant fullSince) implements DomainEvent {
    }

    record WorkerStopMissed(TaskId task, WorkerId worker, Instant stoppedAt, int message, boolean noticeTyped) implements DomainEvent {
    }

    record LimitResumeTyped(TaskId task, WorkerId worker) implements DomainEvent {
    }

    record LimitResumeHeldBack(TaskId task, WorkerId worker, PaneState pane) implements DomainEvent {
    }

    record WorkerDied(TaskId task, WorkerId worker, DeathCause cause) implements DomainEvent {
    }

    record WorkerStale(TaskId task, WorkerId worker, Instant quietSince) implements DomainEvent {
    }

    record WorkerBusyTooLong(TaskId task, WorkerId worker, Instant busySince) implements DomainEvent {
    }

    record HeavyRunsAtOnce(TaskId task, WorkerId worker, HeavyRun run, List<WorkerRun> beside) implements DomainEvent {

        public HeavyRunsAtOnce {
            beside = List.copyOf(beside);
        }
    }

    record DeclaredWaitEnded(TaskId task, WorkerId worker, StatusReport pause) implements DomainEvent {
    }

    record WorkerQuestionUnanswered(TaskId task, WorkerId worker, StatusReport question, Instant askedAt) implements DomainEvent {
    }

    record LostInOutage(TaskId task, WorkerId worker, int number, DeathCause cause) implements DomainEvent {
    }

    record RecoveryChosen(TaskId task, int number, RecoveryAction action) implements DomainEvent {
    }

    record RecoveryFailed(TaskId task, int number, String because) implements DomainEvent {
    }

    record WorkerStopRequested(TaskId task, WorkerId worker) implements DomainEvent {
    }

    record RecoveryCostRecorded(TaskId task, WorkerId worker, LaunchKind launch, TokenUsage tokens) implements DomainEvent {
    }

    record HoldDateArrived(TaskId task, LocalDate until) implements DomainEvent {
    }

    record DeliveryRefused(TaskId task, WorkerId worker, List<String> gaps) implements DomainEvent {

        public DeliveryRefused {
            gaps = List.copyOf(gaps);
        }
    }

    record DraftRecorded(TaskId task, MergeRequestAddress address) implements DomainEvent {
    }

    record DraftOpened(TaskId task, MergeRequestAddress address) implements DomainEvent {
    }

    record DraftMerged(TaskId task, MergeRequestAddress address, String landedCommit) implements DomainEvent {
    }

    record DraftClosed(TaskId task, MergeRequestAddress address) implements DomainEvent {
    }

    record CloneRefreshed(TaskId task, ProjectName project, String commit) implements DomainEvent {
    }

    record ChangeAppliedToCaptain(TaskId task, List<String> files) implements DomainEvent {

        public ChangeAppliedToCaptain {
            files = List.copyOf(files);
        }
    }

    record ChangeNotApplied(TaskId task, String because) implements DomainEvent {
    }

    record CleanupRefused(TaskId task, List<String> findings) implements DomainEvent {

        public CleanupRefused {
            findings = List.copyOf(findings);
        }
    }

    record TaskCleanedUp(TaskId task) implements DomainEvent {
    }

    record TaskCostRecorded(TaskId task, TaskCost cost) implements DomainEvent {
    }

    record TaskCostRecounted(TaskId task, TaskCost before, TaskCost after) implements DomainEvent {
    }

    record PipelineRunSeen(TaskId task, PipelineRun run) implements DomainEvent {
    }

    record PipelineRunFinished(TaskId task, PipelineRun run) implements DomainEvent {
    }

    record TaskKeyed(TaskId task, WorkKey key) implements DomainEvent {
    }

    record TicketCreated(TaskId task, WorkKey key) implements DomainEvent {
    }

    record TicketChoiceRefused(TaskId task, String because) implements DomainEvent {
    }

    record TicketAddedToSprint(TaskId task, WorkKey key, String sprint) implements DomainEvent {
    }

    record TicketLinked(TaskId task, WorkKey key, String url) implements DomainEvent {
    }

    record TicketTransitioned(TaskId task, WorkKey key, String from, String to) implements DomainEvent {
    }

    record TicketUpdateRefused(TaskId task, WorkKey key, String update, String because) implements DomainEvent {
    }

    record ResearchQueuedFirst(TaskId task, TaskId research, CategoryId category) implements DomainEvent {
    }

    record ReviewCounted(TaskId task, PipelineReview review) implements DomainEvent {
    }

    record RecheckAsked(TaskId task, DecisionNumber number, String note) implements DomainEvent {

        public RecheckAsked {
            note = note == null ? "" : note.strip();
        }
    }

    record BoardArmed(TaskId task, BoardId board, String file, List<DecisionNumber> bound) implements DomainEvent {

        public BoardArmed {
            bound = List.copyOf(bound);
        }
    }

    record BoardRoundPosted(TaskId task, BoardId board, String link, int questions) implements DomainEvent {
    }

    record BoardAnswered(TaskId task, BoardId board, int round, int prompts, boolean sessionEnded) implements DomainEvent {
    }

    record BoardClosed(TaskId task, BoardId board) implements DomainEvent {
    }

    record BoardRetired(TaskId task, BoardId board) implements DomainEvent {
    }

    record BoardAnswerRefused(TaskId task, DecisionNumber number, BoardId board, String because) implements DomainEvent {
    }

    record CaptainSaid(TaskId task, String verbatim) implements DomainEvent {
    }

    record ScoutPromoted(TaskId task, TaskId ship) implements DomainEvent {
    }

    record TaskDropped(TaskId task, String captainsWord, List<String> discarded) implements DomainEvent {

        public TaskDropped {
            discarded = List.copyOf(discarded);
        }
    }

    record WorkDiscarded(TaskId task, String captainsWord, List<String> discarded) implements DomainEvent {

        public WorkDiscarded {
            discarded = List.copyOf(discarded);
        }
    }

    record TaskImported(TaskId task, String oldHome) implements DomainEvent {
    }

    record TaskAdopted(TaskId task, BranchName branch, String oldCopy, UUID session) implements DomainEvent {
    }
}
