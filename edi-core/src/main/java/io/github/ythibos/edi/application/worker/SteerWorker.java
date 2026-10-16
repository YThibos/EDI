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
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Keystroke;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class SteerWorker {

    private final TaskStore tasks;
    private final WorkerStore workers;
    private final InboxStore inbox;
    private final EventLog events;
    private final WorkerRuntime runtime;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SteerWorker(TaskStore tasks, WorkerStore workers, InboxStore inbox, EventLog events, WorkerRuntime runtime, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.workers = workers;
        this.inbox = inbox;
        this.events = events;
        this.runtime = runtime;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Steered steer(TaskReference reference, String body, StatusKey resolves, Actor actor) {
        Sent sent = transaction.execute(_ -> sent(tasks.get(reference).id(), body, resolves, actor));
        return new Steered(sent.message(), sent.worker(), deliveryTo(sent.worker(), sent.message()));
    }

    private Sent sent(TaskId task, String body, StatusKey resolves, Actor actor) {
        Instant now = clock.now();
        Worker worker = WorkerLookup.servingTask(workers, task);
        InboxMessage message = InboxMessage.sent(task, inbox.nextNumber(task), body, resolves, now);
        inbox.save(message);
        events.record(new SteerSent(task, message.number(), resolves), actor, now);
        if (resolves != null) {
            recordResolved(worker, message, actor, now);
        }
        return new Sent(message, worker);
    }

    private void recordResolved(Worker worker, InboxMessage message, Actor actor, Instant now) {
        StatusReport resolved = new StatusReport(StatusState.RESOLVED, message.resolves(), null, "resolved by message " + message.number());
        events.record(new StatusReported(message.task(), worker.id(), resolved), actor, now);
    }

    private SteerDelivery deliveryTo(Worker worker, InboxMessage message) {
        if (!runtime.isRunning(worker.window())) {
            return SteerDelivery.WAITING_FOR_A_LIVE_WORKER;
        }
        if (!worker.isIdle()) {
            return SteerDelivery.AT_THE_NEXT_STOP;
        }
        return typedNotice(worker, message).orElse(SteerDelivery.WAITING_FOR_THE_PANE);
    }

    private Optional<SteerDelivery> typedNotice(Worker worker, InboxMessage message) {
        try {
            runtime.press(worker.window(), Keystroke.line(message.notice()));
            return Optional.of(SteerDelivery.TYPED_INTO_THE_IDLE_PROMPT);
        } catch (Refusal paneNotIdle) {
            return Optional.empty();
        }
    }

    public record Steered(InboxMessage message, Worker worker, SteerDelivery delivery) {
    }

    private record Sent(InboxMessage message, Worker worker) {
    }
}
