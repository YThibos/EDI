package io.github.ythibos.edi.application.worker;

import static java.util.function.Predicate.not;

import java.time.Instant;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerQuestionUnanswered;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.PresenceMode;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.worker.SupervisionRules;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class RemindOfUnansweredQuestions {

    private final WorkerStore workers;
    private final InboxStore inbox;
    private final PresenceStore presence;
    private final EventLog events;
    private final MachineProfile machine;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RemindOfUnansweredQuestions(WorkerStore workers, InboxStore inbox, PresenceStore presence, EventLog events, MachineProfile machine, Clock clock,
            TransactionTemplate transaction) {
        this.workers = workers;
        this.inbox = inbox;
        this.presence = presence;
        this.events = events;
        this.machine = machine;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void remind() {
        SupervisionRules rules = machine.supervision();
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            if (theCaptainIsAway()) {
                return;
            }
            workers.serving().stream()
                    .filter(Worker::waitsOnThePrimary)
                    .filter(not(this::hasAResolvingSteerSent))
                    .filter(worker -> rules.remindsOfTheQuestion(worker, lastRaisedOf(worker), now))
                    .forEach(worker -> remindOf(worker, now));
        });
    }

    private boolean theCaptainIsAway() {
        return presence.current().map(Presence::mode).filter(PresenceMode.AWAY::equals).isPresent();
    }

    private boolean hasAResolvingSteerSent(Worker worker) {
        StatusKey question = worker.lastStatus().report().key();
        return inbox.unacknowledgedOf(worker.id().task()).stream().anyMatch(message -> question.equals(message.resolves()));
    }

    private Instant lastRaisedOf(Worker worker) {
        Instant asked = worker.lastStatus().at();
        return events.ofTask(worker.id().task()).stream()
                .filter(event -> event.isA(WorkerQuestionUnanswered.class))
                .filter(event -> worker.id().toString().equals(event.text("worker")))
                .map(LoggedEvent::at)
                .filter(at -> !at.isBefore(asked))
                .max(Comparator.naturalOrder())
                .orElse(asked);
    }

    private void remindOf(Worker worker, Instant now) {
        events.record(new WorkerQuestionUnanswered(worker.id().task(), worker.id(), worker.lastStatus().report(), worker.lastStatus().at()),
                Actor.CORE, now);
    }
}
