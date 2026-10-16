package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class DeliverAcceptedWork {

    private final WorkerContext context;
    private final Forges forges;
    private final CaptainCheckouts checkouts;
    private final DeliveryStore deliveries;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DeliverAcceptedWork(WorkerContext context, Forges forges, CaptainCheckouts checkouts, DeliveryStore deliveries, EventLog events,
            Clock clock, TransactionTemplate transaction) {
        this.context = context;
        this.forges = forges;
        this.checkouts = checkouts;
        this.deliveries = deliveries;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void deliver(TaskId task) {
        deliver(task, Optional.empty());
    }

    public void deliver(TaskId task, Optional<MergeRequestView> claimedDraft) {
        Workplace workplace = transaction.execute(_ -> context.of(task));
        if (!workplace.isAShip()) {
            return;
        }
        switch (workplace.delivery()) {
            case DRAFT_REVIEW -> recordTheDraft(workplace, claimedDraft.orElseGet(() -> openDraftOf(workplace)));
            case LOCAL_BRANCH -> checkouts.deliverTheBranch(workplace);
            case LOCAL_APPLY -> checkouts.applyTheChange(workplace);
        }
    }

    private MergeRequestView openDraftOf(Workplace workplace) {
        RegisteredForge forge = workplace.project().requireForge();
        return forges.serving(forge).openMergeRequestFrom(forge, workplace.dispatch().branch())
                .orElseThrow(() -> new Refusal("No merge request is open from " + workplace.dispatch().branch() + " on " + forge.repository() + "."));
    }

    private void recordTheDraft(Workplace workplace, MergeRequestView draft) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            deliveries.save(Delivery.of(workplace.task().id(), draft, now));
            events.record(new DraftRecorded(workplace.task().id(), draft.address()), Actor.CORE, now);
        });
    }
}
