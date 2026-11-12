package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.task.DependantTasks;
import io.github.ythibos.edi.application.task.TaskStages;
import io.github.ythibos.edi.application.tracker.TicketProposals;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDropped;
import io.github.ythibos.edi.domain.landing.LandedWork;
import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.StageTrigger.Drop;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class DropTask {

    private static final String STOP_IT_FIRST = "stop it with edi worker stop before dropping the task.";

    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final EventLog events;
    private final WorkerContext context;
    private final LandedWorkCheck landedWork;
    private final CopyRetirement retirement;
    private final CaptainsWordEvidence captainsWord;
    private final DependantTasks dependants;
    private final TicketProposals ticketProposals;
    private final TaskStages stages;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DropTask(TaskStore tasks, DispatchStore dispatches, EventLog events, WorkerContext context, LandedWorkCheck landedWork,
            CopyRetirement retirement, CaptainsWordEvidence captainsWord, DependantTasks dependants, TicketProposals ticketProposals,
            TaskStages stages, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.events = events;
        this.context = context;
        this.landedWork = landedWork;
        this.retirement = retirement;
        this.captainsWord = captainsWord;
        this.dependants = dependants;
        this.ticketProposals = ticketProposals;
        this.stages = stages;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DroppedTask drop(TaskReference reference, boolean discardingWork, CallingSession calling, Actor actor) {
        Task task = transaction.execute(_ -> tasks.get(reference));
        CaptainsWord word = captainsWord.requireOnTask(calling, task.id(), discardingWork ? CaptainWordAct.DISCARD_WORK : CaptainWordAct.DROP);
        requireNoDecisionButItsTicketWaiting(task);
        List<String> discarded = wasDispatched(task) ? retiredCopy(transaction.execute(_ -> context.of(task.id())), discardingWork) : List.of();
        DroppedTask dropped = dropped(task, new Drop(word, discarded, discardingWork), actor);
        retirement.stopListeningFor(dropped.task().id());
        return dropped;
    }

    private void requireNoDecisionButItsTicketWaiting(Task task) {
        if (task.isHeld() && task.hold().awaitsADecision() && !awaitsItsTicket(task)) {
            throw new Refusal(task.id() + " waits on " + task.hold().reason() + "; it is dropped once that decision is answered.");
        }
    }

    private boolean awaitsItsTicket(Task task) {
        return Boolean.TRUE.equals(transaction.execute(_ -> ticketProposals.awaitsItsTicket(task.id())));
    }

    private boolean wasDispatched(Task task) {
        return transaction.execute(_ -> dispatches.of(task.id())).isPresent();
    }

    private List<String> retiredCopy(Workplace workplace, boolean discardingWork) {
        retirement.requireNoWorkerAtWork(workplace, STOP_IT_FIRST);
        LandedWork landed = landedWork.of(workplace);
        if (landed.isLanded()) {
            retirement.retireLanded(workplace);
            return List.of();
        }
        if (!discardingWork) {
            throw new Refusal(workplace.task().id() + " is not dropped, because its work is not landed: " + String.join("; ", landed.findings())
                    + ". Drop it with --discard-work only on the captain's word to discard that work.");
        }
        retirement.retireDiscarding(workplace);
        return landed.findings();
    }

    private DroppedTask dropped(Task task, Drop drop, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            DecisionNumber closedDecision = ticketProposals.closeForTheDrop(task.id(), drop.word(), actor, now).orElse(null);
            retirement.retireWorkers(task.id(), "its task was dropped", now, actor);
            retirement.retireBoards(task.id(), now, actor);
            Task closed = stages.moved(tasks.get(task.id()), drop, actor, now);
            dependants.stopWaitingFor(closed);
            events.record(new TaskDropped(closed.id(), drop.word().verbatim(), drop.unlandedWork()), actor, now);
            return new DroppedTask(closed, drop.unlandedWork(), closedDecision);
        });
    }
}
