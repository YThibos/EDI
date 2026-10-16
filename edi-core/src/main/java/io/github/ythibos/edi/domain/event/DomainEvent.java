package io.github.ythibos.edi.domain.event;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.guard.GuardRule;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.PaneState;
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

    record TaskDone(TaskId task) implements DomainEvent {
    }

    record DispatchDeferred(TaskId task, ProjectName project, CategoryId category, int running, int capacity) implements DomainEvent {
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
}
