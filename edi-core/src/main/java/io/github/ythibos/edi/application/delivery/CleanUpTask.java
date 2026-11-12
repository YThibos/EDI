package io.github.ythibos.edi.application.delivery;

import java.nio.file.Files;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PipelineReviewStore;
import io.github.ythibos.edi.adapter.store.TaskCostStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.application.task.DependantTasks;
import io.github.ythibos.edi.application.task.TaskStages;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.CleanupRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.ReviewCounted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCostRecorded;
import io.github.ythibos.edi.domain.landing.LandedWork;
import io.github.ythibos.edi.domain.task.StageTrigger.CleanUp;
import io.github.ythibos.edi.domain.task.StageTrigger.LandingObserved;
import io.github.ythibos.edi.domain.task.StageTrigger.RequestMerged;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskStage;

@Service
public class CleanUpTask {

    private static final String HANDED_OVER = "it is cleaned up once its done is accepted or the forge shows its merge request merged.";

    private final TaskStore tasks;
    private final DeliveryStore deliveries;
    private final TaskCostStore costs;
    private final PipelineReviewStore reviews;
    private final EventLog events;
    private final WorkerContext context;
    private final LandedWorkCheck landedWork;
    private final TaskCosts taskCosts;
    private final Pipeline pipeline;
    private final CopyRetirement retirement;
    private final DependantTasks dependants;
    private final TaskStages stages;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CleanUpTask(TaskStore tasks, DeliveryStore deliveries, TaskCostStore costs, PipelineReviewStore reviews,
            EventLog events, WorkerContext context, LandedWorkCheck landedWork, TaskCosts taskCosts, Pipeline pipeline, CopyRetirement retirement,
            DependantTasks dependants, TaskStages stages, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.deliveries = deliveries;
        this.costs = costs;
        this.reviews = reviews;
        this.events = events;
        this.context = context;
        this.landedWork = landedWork;
        this.taskCosts = taskCosts;
        this.pipeline = pipeline;
        this.retirement = retirement;
        this.dependants = dependants;
        this.stages = stages;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Task cleanUp(TaskReference reference, Actor actor) {
        Workplace workplace = transaction.execute(_ -> context.of(tasks.get(reference).id()));
        workplace.task().requireReadyForCleanUp();
        requireTheWorkHandedOver(workplace);
        requireTheReportOfAScout(workplace);
        removeTheNestedRepositoriesOfAScout(workplace);
        Landing landing = landingOf(workplace, actor);
        TaskCost cost = taskCosts.countFor(workplace);
        PipelineReview review = reviewOf(workplace);
        retirement.retireLanded(workplace);
        Task closed = closed(workplace, landing, cost, review, actor);
        retirement.stopListeningFor(closed.id());
        return closed;
    }

    private void requireTheWorkHandedOver(Workplace workplace) {
        if (!isMerged(workplace)) {
            retirement.requireNoWorkerAtWork(workplace, HANDED_OVER);
        }
    }

    private boolean isMerged(Workplace workplace) {
        return transaction.execute(_ -> deliveries.of(workplace.task().id())).filter(Delivery::isMerged).isPresent();
    }

    private void requireTheReportOfAScout(Workplace workplace) {
        if (workplace.task().intake().kind() != TaskKind.SCOUT) {
            return;
        }
        if (workplace.task().stage() != TaskStage.DELIVERED || !Files.isRegularFile(workplace.report())) {
            throw new Refusal(workplace.task().id() + " is a scout, and a scout is cleaned up only after its done was accepted and its report "
                    + workplace.report() + " exists.");
        }
    }

    private void removeTheNestedRepositoriesOfAScout(Workplace workplace) {
        if (workplace.task().intake().kind() == TaskKind.SCOUT) {
            retirement.removeNestedRepositories(workplace);
        }
    }

    private Landing landingOf(Workplace workplace, Actor actor) {
        LandedWork landed = landedWork.of(workplace);
        requireLanded(workplace, landed, actor);
        return new Landing(landed, landedWork.headOf(workplace).orElse(workplace.baseRef()));
    }

    private void requireLanded(Workplace workplace, LandedWork landed, Actor actor) {
        if (landed.isLanded()) {
            return;
        }
        transaction.executeWithoutResult(_ -> events.record(new CleanupRefused(workplace.task().id(), landed.findings()), actor, clock.now()));
        throw new Refusal(workplace.task().id() + " is not cleaned up, because its work is not landed: " + String.join("; ", landed.findings())
                + ". Nothing is discarded without the captain's word.");
    }

    private PipelineReview reviewOf(Workplace workplace) {
        return pipeline.reviewOf(workplace.project().projectClone(), workplace.dispatch().branch()).orElse(PipelineReview.unknown());
    }

    private Task closed(Workplace workplace, Landing landing, TaskCost cost, PipelineReview review, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            TaskId id = workplace.task().id();
            retirement.retireWorkers(id, "its task was cleaned up", now, actor);
            retirement.retireBoards(id, now, actor);
            Task landed = landedOnItsBase(tasks.get(id), landing, actor, now);
            Task cleaned = stages.moved(landed, retirementOf(workplace), actor, now);
            dependants.stopWaitingFor(cleaned);
            costs.save(cost);
            events.record(new TaskCostRecorded(cleaned.id(), cost), actor, now);
            reviews.save(cleaned.id(), review, now);
            events.record(new ReviewCounted(cleaned.id(), review), actor, now);
            events.record(new TaskCleanedUp(cleaned.id()), actor, now);
            return cleaned;
        });
    }

    private Task landedOnItsBase(Task task, Landing landing, Actor actor, Instant now) {
        if (task.intake().kind() == TaskKind.SCOUT) {
            return task;
        }
        Task merged = task.stage().isMergeable() ? stages.moved(task, landing.asAMerge(), actor, now) : task;
        return merged.stage() == TaskStage.MERGED ? stages.moved(merged, landing.asObserved(), actor, now) : merged;
    }

    private CleanUp retirementOf(Workplace workplace) {
        return new CleanUp(retirement.leftoversOf(workplace), Files.isRegularFile(workplace.report()));
    }

    private record Landing(LandedWork work, String head) {

        RequestMerged asAMerge() {
            return new RequestMerged(head, work.unlandedCommits());
        }

        LandingObserved asObserved() {
            return new LandingObserved(work.isLanded(), work.unlandedCommits());
        }
    }
}
