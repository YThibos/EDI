package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkDiscarded;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.CaptainsWord;

@Service
public class DiscardWork {

    private static final String STOP_IT_FIRST = "stop it with edi worker stop before discarding its work.";

    private final TaskStore tasks;
    private final EventLog events;
    private final WorkerContext context;
    private final LandedWorkCheck landedWork;
    private final CopyRetirement retirement;
    private final CaptainsWordEvidence captainsWord;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DiscardWork(TaskStore tasks, EventLog events, WorkerContext context, LandedWorkCheck landedWork, CopyRetirement retirement,
            CaptainsWordEvidence captainsWord, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.events = events;
        this.context = context;
        this.landedWork = landedWork;
        this.retirement = retirement;
        this.captainsWord = captainsWord;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DiscardedWork discard(TaskReference reference, CallingSession calling, Actor actor) {
        Workplace workplace = transaction.execute(_ -> context.of(tasks.get(reference).id()));
        CaptainsWord word = captainsWord.requireOnTask(calling, workplace.task().id(), CaptainWordAct.DISCARD_WORK);
        requireInFlight(workplace);
        retirement.requireNoWorkerAtWork(workplace, STOP_IT_FIRST);
        List<String> discarded = landedWork.of(workplace).findings();
        retirement.retireDiscarding(workplace);
        recordDiscarded(workplace, word, discarded, actor);
        return new DiscardedWork(workplace.task().id(), discarded);
    }

    private static void requireInFlight(Workplace workplace) {
        if (workplace.task().isClosed()) {
            throw new Refusal(Rule.TASK_STAGE, workplace.task().id() + " is " + workplace.task().state().word() + ", so it keeps no isolated copy to discard.");
        }
    }

    private void recordDiscarded(Workplace workplace, CaptainsWord word, List<String> discarded, Actor actor) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            retirement.retireWorkers(workplace.task().id(), "its work was discarded", now, actor);
            events.record(new WorkDiscarded(workplace.task().id(), word.verbatim(), discarded), actor, now);
        });
    }
}
