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
import io.github.ythibos.edi.application.tracker.TaskKeys;
import io.github.ythibos.edi.application.tracker.TicketProposals;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.project.Project;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.UnknownTask;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketProposal;
import io.github.ythibos.edi.domain.tracker.WorkKeying;

@Service
public class AddTask {

    private final TaskStore tasks;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final TaskKeys keys;
    private final TicketProposals proposals;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AddTask(TaskStore tasks, EventLog events, ProjectRegistry registry, TaskKeys keys, TicketProposals proposals, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.events = events;
        this.registry = registry;
        this.keys = keys;
        this.proposals = proposals;
        this.clock = clock;
        this.transaction = transaction;
    }

    public AddedTask add(NewTask newTask, Actor actor) {
        requireItsProject(newTask.intake());
        requireRegistered(newTask.intake().projects());
        WorkKeying keying = keys.keyingFor(newTask.intake(), newTask.notes(), newTask.ticket());
        return transaction.execute(_ -> {
            requireExisting(newTask.intake().blockedBy());
            Task task = queued(newTask, keyedIntake(newTask.intake(), keying));
            tasks.insert(task);
            events.record(new TaskAdded(task.id(), task.intake()), actor, task.created());
            return new AddedTask(task.id(), ticketOf(keying, task), ticketDecisionFor(task, keying, actor));
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

    private Task queued(NewTask newTask, Intake intake) {
        return Task.queued(idFor(newTask), intake, newTask.notes(), clock.now());
    }

    private Intake keyedIntake(Intake intake, WorkKeying keying) {
        return keying instanceof WorkKeying.ToIssue issue ? intake.keyedOn(keys.issue(intake.projects().getFirst(), issue.draft())) : intake;
    }

    private static Ticket ticketOf(WorkKeying keying, Task task) {
        return switch (keying) {
            case WorkKeying.Given given -> given.ticket();
            case WorkKeying.ToIssue _ -> Ticket.keyedOnly(task.intake().workKey());
            case WorkKeying.ToPropose _, WorkKeying.Unkeyed _ -> null;
        };
    }

    private DecisionNumber ticketDecisionFor(Task task, WorkKeying keying, Actor actor) {
        return keying instanceof WorkKeying.ToPropose propose
                ? proposals.propose(new TicketProposal(task.id(), propose.draft(), propose.candidates()), actor)
                : null;
    }

    private TaskId idFor(NewTask newTask) {
        if (newTask.chosenId() == null) {
            return tasks.firstFreeIdFrom(TaskId.sluggedFrom(newTask.intake().title()));
        }
        if (tasks.contains(newTask.chosenId())) {
            throw new Refusal(newTask.chosenId() + " already exists, and a task id is never reused.");
        }
        return newTask.chosenId();
    }
}
