package io.github.ythibos.edi.application.decision;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.task.DecisionReleases;
import io.github.ythibos.edi.application.task.DependantTasks;
import io.github.ythibos.edi.application.tracker.TicketProposals;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.tracker.TicketSettlement;

@Service
public class AnswerDecision {

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final EventLog events;
    private final TicketProposals proposals;
    private final DependantTasks dependants;
    private final DecisionReleases releases;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AnswerDecision(TaskStore tasks, DecisionStore decisions, EventLog events, TicketProposals proposals, DependantTasks dependants,
            DecisionReleases releases, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.events = events;
        this.proposals = proposals;
        this.dependants = dependants;
        this.releases = releases;
        this.clock = clock;
        this.transaction = transaction;
    }

    public AnsweredDecision answer(TaskReference reference, GivenAnswer given, Actor actor) {
        AnsweredDecision answered = transaction.execute(_ -> {
            Instant now = clock.now();
            Task task = tasks.get(reference);
            proposals.requireAChoiceIn(task.id(), given.option());
            Decision decided = decisionOf(reference, task).answered(given.recordedAt(now));
            decisions.save(decided);
            events.record(new DecisionAnswered(task.id(), decided.number(), decided.answer()), actor, now);
            Task released = releases.released(task, actor, now);
            releaseTheDependantsOfADoneTask(released);
            recordTheDone(released, actor, now);
            return new AnsweredDecision(decided, released, TicketSettlement.none());
        });
        return answered.withTicket(proposals.settle(answered.task().id(), actor));
    }

    private Decision decisionOf(TaskReference reference, Task task) {
        return decisions.namedBy(reference).orElseThrow(() -> new UnknownDecision(task.id()));
    }

    private void releaseTheDependantsOfADoneTask(Task released) {
        if (released.isDone()) {
            dependants.stopWaitingFor(released);
        }
    }

    private void recordTheDone(Task released, Actor actor, Instant now) {
        if (released.isDone()) {
            events.record(new TaskDone(released.id()), actor, now);
        }
    }
}
