package io.github.ythibos.edi.web;

import java.util.List;

import io.github.ythibos.edi.application.delivery.DeliverDraft.DeliveredDraft;
import io.github.ythibos.edi.application.delivery.DiscardedWork;
import io.github.ythibos.edi.application.delivery.DroppedTask;
import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.migration.AdoptedTask;
import io.github.ythibos.edi.application.task.AddedTask;
import io.github.ythibos.edi.application.task.PromotedScout;
import io.github.ythibos.edi.application.tracker.KeyedTask;
import io.github.ythibos.edi.application.worker.SteerWorker;
import io.github.ythibos.edi.application.worker.StopWorker;
import io.github.ythibos.edi.domain.authority.Authorisation;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;
import io.github.ythibos.edi.domain.worker.Worker;

public sealed interface Confirmation {

    record Queued(AddedTask added) implements Confirmation {
    }

    record Noted(TaskId task) implements Confirmation {
    }

    record Held(Task task) implements Confirmation {
    }

    record Released(Task task) implements Confirmation {
    }

    record Raised(DecisionNumber number) implements Confirmation {
    }

    record Answered(Decision decision, Task task, TicketSettlement ticket) implements Confirmation {
    }

    record Dispatched(DispatchOutcome.Launched launched) implements Confirmation {
    }

    record PlannedFirst(DispatchOutcome research) implements Confirmation {
    }

    record Deferred(DispatchOutcome.Deferred deferred) implements Confirmation {
    }

    record StatusRecorded(Worker worker) implements Confirmation {
    }

    record Acknowledged(InboxMessage message) implements Confirmation {
    }

    record Steered(SteerWorker.Steered steered) implements Confirmation {
    }

    record Relaunched(DispatchOutcome.Launched launched) implements Confirmation {
    }

    record StopRequested(StopWorker.StoppedWorker stopped) implements Confirmation {
    }

    record DraftDelivered(DeliveredDraft delivered) implements Confirmation {
    }

    record MergedWorkFollowedUp(MergedWork work) implements Confirmation {
    }

    record CleanedUp(Task task) implements Confirmation {
    }

    record BoardArmed(Board board) implements Confirmation {
    }

    record BoardRoundPosted(Board board) implements Confirmation {
    }

    record RecheckAsked(DecisionNumber number) implements Confirmation {
    }

    record CaptainHeard(Authorisation word) implements Confirmation {
    }

    record Promoted(PromotedScout promoted) implements Confirmation {
    }

    record Dropped(DroppedTask dropped) implements Confirmation {
    }

    record Keyed(KeyedTask keyed) implements Confirmation {
    }

    record Adopted(AdoptedTask adopted) implements Confirmation {
    }

    record WorkDiscarded(DiscardedWork discarded) implements Confirmation {
    }

    record CoreStopping(List<Worker> stoppedWorkers) implements Confirmation {

        public CoreStopping {
            stoppedWorkers = List.copyOf(stoppedWorkers);
        }
    }
}
