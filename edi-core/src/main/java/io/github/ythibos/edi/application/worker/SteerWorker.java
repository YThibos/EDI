package io.github.ythibos.edi.application.worker;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerQuestion;

@Service
public class SteerWorker {

    private final TaskStore tasks;
    private final WorkerStore workers;
    private final InboxStore inbox;
    private final EventLog events;
    private final InboxNotice notice;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SteerWorker(TaskStore tasks, WorkerStore workers, InboxStore inbox, EventLog events, InboxNotice notice, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workers = workers;
        this.inbox = inbox;
        this.events = events;
        this.notice = notice;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Steered steer(TaskReference reference, String body, StatusKey resolves, Actor actor) {
        StoredSteer stored = transaction.execute(_ -> stored(WorkerLookup.servingTask(workers, tasks.get(reference).id()), body, resolves, actor));
        return announced(stored);
    }

    public Optional<StoredSteer> carryTheAnswerOf(Decision answered, Actor actor) {
        return transaction.execute(_ -> WorkerLookup.servingWorkerOf(workers, answered.task())
                .map(worker -> stored(worker, answered.answerForItsWorker(), questionAnsweredBy(answered, worker), actor)));
    }

    public Steered announced(StoredSteer stored) {
        return new Steered(stored.message(), stored.worker(), notice.deliver(stored.worker(), stored.message()));
    }

    private StoredSteer stored(Worker worker, String body, StatusKey resolves, Actor actor) {
        Instant now = clock.now();
        TaskId task = worker.id().task();
        InboxMessage message = InboxMessage.sent(task, inbox.nextNumber(task), body, resolves, now);
        inbox.save(message);
        events.record(new SteerSent(task, message.number(), resolves), actor, now);
        if (resolves != null) {
            recordResolved(worker, message, actor, now);
        }
        return new StoredSteer(message, worker);
    }

    private void recordResolved(Worker worker, InboxMessage message, Actor actor, Instant now) {
        StatusReport resolved = StatusReport.resolvedBy(message.resolves(), message.number());
        events.record(new StatusReported(message.task(), worker.id(), resolved), actor, now);
    }

    private static StatusKey questionAnsweredBy(Decision answered, Worker worker) {
        return worker.openQuestion().filter(answered::answers).map(WorkerQuestion::key).orElse(null);
    }

    public record Steered(InboxMessage message, Worker worker, SteerDelivery delivery) {
    }

    public record StoredSteer(InboxMessage message, Worker worker) {
    }
}
