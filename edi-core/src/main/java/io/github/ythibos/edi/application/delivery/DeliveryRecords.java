package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class DeliveryRecords {

    private final Forges forges;
    private final DeliveryStore deliveries;
    private final EventLog events;
    private final Clock clock;

    public DeliveryRecords(Forges forges, DeliveryStore deliveries, EventLog events, Clock clock) {
        this.forges = forges;
        this.deliveries = deliveries;
        this.events = events;
        this.clock = clock;
    }

    public boolean followsARequest(Workplace workplace) {
        return workplace.isAShip() && workplace.delivery() == DeliveryMode.DRAFT_REVIEW && workplace.project().forge() != null;
    }

    public boolean awaitsTheRequestOf(Workplace workplace) {
        return followsARequest(workplace) && deliveries.of(workplace.task().id()).filter(delivery -> !delivery.isClosed()).isEmpty();
    }

    public Optional<MergeRequestView> requestFrom(Workplace workplace) {
        RegisteredForge forge = workplace.project().requireForge();
        return forges.serving(forge).mergeRequestFrom(forge, workplace.dispatch().branch());
    }

    public MergeRequestView requireTheRequestFrom(Workplace workplace) {
        return requestFrom(workplace).orElseThrow(() -> new Refusal("EDI has no merge request to follow: " + noRequestFrom(workplace) + "."));
    }

    public String noRequestFrom(Workplace workplace) {
        return "no merge request from " + workplace.dispatch().branch() + " is open or merged on " + workplace.project().requireForge().repository();
    }

    public Delivery recorded(TaskId task, MergeRequestView request, Actor actor) {
        return recorded(task, request, new DraftRecorded(task, request.address()), actor);
    }

    public Delivery recorded(TaskId task, MergeRequestView request, DomainEvent event, Actor actor) {
        Instant now = clock.now();
        Delivery delivery = Delivery.of(task, request, now);
        deliveries.save(delivery);
        events.record(event, actor, now);
        return delivery;
    }
}
