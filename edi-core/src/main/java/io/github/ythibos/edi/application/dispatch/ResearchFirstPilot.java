package io.github.ythibos.edi.application.dispatch;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.PipelineReviewStore;
import io.github.ythibos.edi.adapter.store.TaskCostStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.UnplannedShipStore;
import io.github.ythibos.edi.application.decision.RaiseDecision;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.PilotComparison;
import io.github.ythibos.edi.domain.dispatch.ShipMeasure;
import io.github.ythibos.edi.domain.dispatch.UnplannedShip;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

@Component
public class ResearchFirstPilot {

    public static final String FOLLOWER = "research-first-pilot";
    public static final TaskId DECISION_TASK = new TaskId("research-first-pilot");

    private static final String DECISION_TITLE = "Decide whether research before implementation becomes the rule";

    private final UnplannedShipStore unplannedShips;
    private final TaskStore tasks;
    private final TaskCostStore costs;
    private final PipelineReviewStore reviews;
    private final DispatchStore dispatches;
    private final RaiseDecision raiseDecision;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ResearchFirstPilot(UnplannedShipStore unplannedShips, TaskStore tasks, TaskCostStore costs, PipelineReviewStore reviews,
            DispatchStore dispatches, RaiseDecision raiseDecision, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.unplannedShips = unplannedShips;
        this.tasks = tasks;
        this.costs = costs;
        this.reviews = reviews;
        this.dispatches = dispatches;
        this.raiseDecision = raiseDecision;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void follow(LoggedEvent event) {
        if (event.isA(TaskCleanedUp.class)) {
            transaction.executeWithoutResult(_ -> decideOnceTheComparisonIsReady());
        }
    }

    private void decideOnceTheComparisonIsReady() {
        if (tasks.contains(DECISION_TASK)) {
            return;
        }
        PilotComparison comparison = comparison();
        if (comparison.isReady()) {
            raiseTheDecision(comparison);
        }
    }

    private PilotComparison comparison() {
        Map<Boolean, List<ShipMeasure>> measured = unplannedShips.finished().stream()
                .collect(Collectors.partitioningBy(UnplannedShip::isPiloted, Collectors.mapping(this::measureOf, Collectors.toList())));
        return new PilotComparison(measured.get(true), measured.get(false));
    }

    private void raiseTheDecision(PilotComparison comparison) {
        Instant now = clock.now();
        Task decisionTask = Task.queued(DECISION_TASK, new Intake(TaskKind.CAPTAIN, DECISION_TITLE, List.of(), null, null, List.of(), null), "", now);
        tasks.insert(decisionTask);
        events.record(new TaskAdded(decisionTask.id(), decisionTask.intake()), Actor.CORE, now);
        raiseDecision.raise(new TaskReference.ById(DECISION_TASK), comparison.card(), Actor.CORE);
    }

    private ShipMeasure measureOf(UnplannedShip unplanned) {
        PipelineReview review = reviews.of(unplanned.ship()).orElse(PipelineReview.unknown());
        return new ShipMeasure(unplanned.ship(), tokensOf(unplanned), review.rounds(), review.findings(), reviewFixesOf(unplanned.ship()));
    }

    private Long tokensOf(UnplannedShip unplanned) {
        Long ship = totalSpentOn(unplanned.ship());
        if (!unplanned.isPiloted() || ship == null) {
            return ship;
        }
        Long research = totalSpentOn(unplanned.research());
        return research == null ? null : ship + research;
    }

    private Long totalSpentOn(TaskId task) {
        return costs.of(task).flatMap(TaskCost::totalIfKnown).orElse(null);
    }

    private int reviewFixesOf(TaskId ship) {
        WorkKey key = tasks.get(ship).intake().workKey();
        return key == null ? 0 : dispatches.countIn(CategoryId.REVIEW_FIX, key, ship);
    }
}
