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
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDropped;
import io.github.ythibos.edi.domain.landing.LandedWork;
import io.github.ythibos.edi.domain.task.CaptainsWord;
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
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DropTask(TaskStore tasks, DispatchStore dispatches, EventLog events, WorkerContext context, LandedWorkCheck landedWork,
            CopyRetirement retirement, CaptainsWordEvidence captainsWord, DependantTasks dependants, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.events = events;
        this.context = context;
        this.landedWork = landedWork;
        this.retirement = retirement;
        this.captainsWord = captainsWord;
        this.dependants = dependants;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DroppedTask drop(TaskReference reference, boolean discardingWork, CallingSession calling, Actor actor) {
        Task task = transaction.execute(_ -> tasks.get(reference));
        CaptainsWord word = captainsWord.requireOnTask(calling, task.id(), discardingWork ? CaptainWordAct.DISCARD_WORK : CaptainWordAct.DROP);
        requireNoDecisionWaiting(task);
        List<String> discarded = wasDispatched(task) ? retiredCopy(transaction.execute(_ -> context.of(task.id())), discardingWork) : List.of();
        Task dropped = dropped(task, word, discarded, actor);
        retirement.stopListeningFor(dropped.id());
        return new DroppedTask(dropped, discarded);
    }

    private static void requireNoDecisionWaiting(Task task) {
        if (task.isHeld() && task.hold().awaitsADecision()) {
            throw new Refusal(task.id() + " waits on " + task.hold().reason() + "; it is dropped once that decision is answered.");
        }
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

    private Task dropped(Task task, CaptainsWord word, List<String> discarded, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Task closed = tasks.get(task.id()).dropped(word, now);
            retirement.retireWorkers(closed.id(), "its task was dropped", now, actor);
            retirement.retireBoards(closed.id(), now, actor);
            tasks.update(closed);
            dependants.stopWaitingFor(closed);
            events.record(new TaskDropped(closed.id(), word.verbatim(), discarded), actor, now);
            return closed;
        });
    }
}
