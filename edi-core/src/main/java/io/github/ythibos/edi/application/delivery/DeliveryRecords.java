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
        return followsARequest(workplace) && followedDeliveryOf(workplace.task().id()).isEmpty();
    }

    public Optional<Delivery> followedDeliveryOf(TaskId task) {
        return deliveries.of(task).filter(delivery -> !delivery.isClosed());
    }

    public Optional<MergeRequestView> requestFrom(Workplace workplace) {
        RegisteredForge forge = workplace.project().requireForge();
        return MergeRequestView.deliveryAmong(forges.serving(forge).mergeRequestsFrom(forge, workplace.dispatch().branch()),
                merged -> isTheMergeOf(workplace, merged));
    }

    private boolean isTheMergeOf(Workplace workplace, MergeRequestView merged) {
        return merged.mergedSince(workplace.dispatch().requestedAt()) && !deliversAnotherTask(workplace.task().id(), merged);
    }

    private boolean deliversAnotherTask(TaskId task, MergeRequestView request) {
        return deliveries.at(request.address().url()).filter(delivery -> !delivery.task().equals(task)).isPresent();
    }

    public MergeRequestView requireTheRequestFrom(Workplace workplace) {
        return requestFrom(workplace).orElseThrow(() -> new Refusal("There is no merge request to follow: " + noRequestFrom(workplace) + "."));
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
