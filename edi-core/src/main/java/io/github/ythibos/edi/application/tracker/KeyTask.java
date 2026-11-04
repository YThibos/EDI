package io.github.ythibos.edi.application.tracker;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.authority.CaptainsWordEvidence;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskKeyed;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class KeyTask {

    private final TaskStore tasks;
    private final EventLog events;
    private final TaskKeys taskKeys;
    private final TicketProposals ticketProposals;
    private final CaptainsWordEvidence captainsWord;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public KeyTask(TaskStore tasks, EventLog events, TaskKeys taskKeys, TicketProposals ticketProposals, CaptainsWordEvidence captainsWord, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.events = events;
        this.taskKeys = taskKeys;
        this.ticketProposals = ticketProposals;
        this.captainsWord = captainsWord;
        this.clock = clock;
        this.transaction = transaction;
    }

    public KeyedTask key(TaskReference reference, WorkKey key, Citation captainsWords, CallingSession calling, Actor actor) {
        Task task = transaction.execute(_ -> tasks.get(reference));
        WorkKey verified = taskKeys.verified(task.intake(), key).key();
        String cited = captainsWords == null ? null : captainsWord.requireCited(calling, captainsWords);
        return transaction.execute(_ -> keyed(task.id(), verified, cited, actor));
    }

    private KeyedTask keyed(TaskId id, WorkKey key, String captainsWords, Actor actor) {
        Instant now = clock.now();
        Task keyed = tasks.get(id).keyedOn(key);
        Optional<DecisionNumber> closed = ticketProposals.closeOnTheKey(id, key, captainsWords, actor, now);
        Task released = closed.isPresent() ? keyed.decisionAnswered(now) : keyed;
        tasks.update(released);
        events.record(new TaskKeyed(id, key), actor, now);
        return new KeyedTask(released, closed.orElse(null));
    }
}
