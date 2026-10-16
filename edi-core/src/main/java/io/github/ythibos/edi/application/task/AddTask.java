package io.github.ythibos.edi.application.task;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.UnknownTask;

@Service
public class AddTask {

    private final TaskStore tasks;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AddTask(TaskStore tasks, EventLog events, ProjectRegistry registry, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.events = events;
        this.registry = registry;
        this.clock = clock;
        this.transaction = transaction;
    }

    public TaskId add(NewTask newTask, Actor actor) {
        requireItsProject(newTask.intake());
        requireRegistered(newTask.intake().projects());
        return transaction.execute(_ -> {
            requireExisting(newTask.intake().blockedBy());
            Task task = Task.queued(idFor(newTask), newTask.intake(), newTask.notes(), clock.now());
            tasks.insert(task);
            events.record(new TaskAdded(task.id(), task.intake()), actor, task.created());
            return task.id();
        });
    }

    private static void requireItsProject(Intake intake) {
        if (!intake.namesTheProjectItWorksOn()) {
            throw new Refusal("A " + intake.kind().word() + " names the project it works on.");
        }
    }

    private void requireRegistered(List<ProjectName> projects) {
        Set<ProjectName> registered = registry.projects().stream().map(Project::name).collect(Collectors.toSet());
        projects.stream()
                .filter(project -> !registered.contains(project))
                .findFirst()
                .ifPresent(unregistered -> {
                    throw new Refusal(unregistered + " is not a registered project; add it to the project registry first.");
                });
    }

    private void requireExisting(List<TaskId> blockers) {
        blockers.stream()
                .filter(blocker -> !tasks.contains(blocker))
                .findFirst()
                .ifPresent(unknown -> {
                    throw new UnknownTask(unknown);
                });
    }

    private TaskId idFor(NewTask newTask) {
        if (newTask.chosenId() == null) {
            return freeIdFrom(TaskId.sluggedFrom(newTask.intake().title()));
        }
        if (tasks.contains(newTask.chosenId())) {
            throw new Refusal(newTask.chosenId() + " already exists, and a task id is never reused.");
        }
        return newTask.chosenId();
    }

    private TaskId freeIdFrom(TaskId slug) {
        TaskId candidate = slug;
        for (int suffix = 2; tasks.contains(candidate); suffix++) {
            candidate = slug.withSuffix(suffix);
        }
        return candidate;
    }
}
