package io.github.ythibos.edi.application.plan;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PlanStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.task.AddTask;
import io.github.ythibos.edi.application.task.CheckedTask;
import io.github.ythibos.edi.application.task.NewTask;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.SlicePlanned;
import io.github.ythibos.edi.domain.plan.Plan;
import io.github.ythibos.edi.domain.plan.PlanDraft;
import io.github.ythibos.edi.domain.plan.PlanHeading;
import io.github.ythibos.edi.domain.plan.Slice;
import io.github.ythibos.edi.domain.plan.SliceDraft;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

@Service
public class AddPlan {

    private final AddTask addTask;
    private final PlanStore plans;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AddPlan(AddTask addTask, PlanStore plans, EventLog events, ProjectRegistry registry, Clock clock, TransactionTemplate transaction) {
        this.addTask = addTask;
        this.plans = plans;
        this.events = events;
        this.registry = registry;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Plan add(PlanDraft draft, Actor actor) {
        requireRoomWithinTheProjects(draft.heading());
        List<CheckedTask> slices = draft.slices().stream().map(slice -> addTask.checked(sliceTask(draft.heading(), slice))).toList();
        return transaction.execute(_ -> planned(draft, slices, actor));
    }

    private void requireRoomWithinTheProjects(PlanHeading heading) {
        OptionalInt projectsCapacity = registry.entryOf(heading.project()).workerCapacity();
        if (projectsCapacity.isPresent() && heading.capacity() > projectsCapacity.getAsInt()) {
            throw new Refusal(Rule.PLAN_INTAKE, "The plan " + heading.key() + " would work on " + heading.capacity() + " slices at once, and "
                    + heading.project() + " runs at most " + projectsCapacity.getAsInt() + " workers at a time.");
        }
    }

    private static NewTask sliceTask(PlanHeading heading, SliceDraft slice) {
        return new NewTask(null, new Intake(TaskKind.SHIP, slice.title(), List.of(heading.project()), slice.key(), null, List.of(), null), slice.body());
    }

    private Plan planned(PlanDraft draft, List<CheckedTask> slices, Actor actor) {
        requireANewKey(draft.heading().key());
        List<TaskId> tasks = slices.stream().map(slice -> addTask.queued(slice, actor).id()).toList();
        Plan plan = draft.planned(tasks);
        Instant now = clock.now();
        plans.insert(plan, now);
        plan.slices().forEach(slice -> events.record(slicePlanned(plan, slice), actor, now));
        return plan;
    }

    private void requireANewKey(WorkKey key) {
        if (plans.contains(key)) {
            throw new Refusal(Rule.PLAN_INTAKE, "A plan is already known by " + key + "; edi plan show " + key + " prints it.");
        }
    }

    private static SlicePlanned slicePlanned(Plan plan, Slice slice) {
        return new SlicePlanned(slice.task(), plan.key(), slice.number(), slice.awaits());
    }
}
