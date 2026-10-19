package io.github.ythibos.edi.application.migration;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkKeySequenceStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskImported;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class BacklogImport {

    private static final ProjectKey EDI = new ProjectKey("EDI");

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final WorkKeySequenceStore workKeys;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public BacklogImport(TaskStore tasks, DecisionStore decisions, WorkKeySequenceStore workKeys, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.workKeys = workKeys;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    BacklogSurvey survey(MigrationPlan plan) {
        return transaction.execute(_ -> {
            Set<TaskId> planned = plan.tasks().stream().map(Task::id).collect(Collectors.toSet());
            Map<DecisionNumber, TaskId> plannedDecisions = plan.decisions().stream().collect(Collectors.toMap(Decision::number, Decision::task));
            List<TaskId> present = tasks.ids();
            Map<DecisionNumber, TaskId> held = heldDecisions();
            return new BacklogSurvey(
                    plan.tasks().stream().map(Task::id).filter(present::contains).toList(),
                    present.stream().filter(task -> !planned.contains(task)).toList(),
                    held.keySet().stream().filter(number -> !held.get(number).equals(plannedDecisions.get(number)))
                            .sorted(Comparator.comparingInt(DecisionNumber::value)).toList(),
                    conflictsOf(plan.decisions(), held),
                    decisions.lastNumberIssued(),
                    workKeys.upcoming(EDI));
        });
    }

    public List<TaskId> importInto(List<MigratedEntry> entries, Path oldHome, Actor actor) {
        return transaction.execute(_ -> {
            List<MigratedEntry> importing = notYetInEdi(entries);
            Instant now = clock.now();
            importing.forEach(entry -> insertedWithoutItsBlockers(entry.task(), oldHome, now, actor));
            importing.forEach(entry -> tasks.update(withBlockersInEdi(entry.task())));
            importing.stream().map(MigratedEntry::decision).filter(Objects::nonNull).forEach(this::saved);
            return importing.stream().map(entry -> entry.task().id()).toList();
        });
    }

    void continueTheSequences(MigrationPlan plan) {
        transaction.executeWithoutResult(_ -> {
            decisions.continueAfter(plan.decisionSequence().highest());
            Optional.ofNullable(plan.highestEdiKey()).ifPresent(workKeys::continueAfter);
        });
    }

    static List<MigratedEntry> entriesOf(MigrationPlan plan) {
        Map<TaskId, Decision> decisionOf = plan.decisions().stream().collect(Collectors.toMap(Decision::task, Function.identity()));
        return plan.tasks().stream().map(task -> new MigratedEntry(task, decisionOf.get(task.id()))).toList();
    }

    private Map<DecisionNumber, TaskId> heldDecisions() {
        return decisions.all().stream().collect(Collectors.toMap(Decision::number, Decision::task));
    }

    private static List<MigrationNote> conflictsOf(List<Decision> planned, Map<DecisionNumber, TaskId> held) {
        return planned.stream()
                .filter(decision -> held.containsKey(decision.number()) && !held.get(decision.number()).equals(decision.task()))
                .map(decision -> new MigrationNote(decision.number().toString(), "is already EDI's decision on " + held.get(decision.number())
                        + ", so " + decision.task() + " and its decision are not imported"))
                .toList();
    }

    private List<MigratedEntry> notYetInEdi(List<MigratedEntry> entries) {
        Set<TaskId> present = Set.copyOf(tasks.ids());
        Map<DecisionNumber, TaskId> held = heldDecisions();
        List<MigratedEntry> importing = new ArrayList<>();
        for (MigratedEntry entry : entries) {
            Decision decision = entry.decision();
            if (!present.contains(entry.task().id()) && (decision == null || !held.containsKey(decision.number()))) {
                importing.add(entry);
            }
        }
        return importing;
    }

    private void insertedWithoutItsBlockers(Task task, Path oldHome, Instant now, Actor actor) {
        tasks.insert(task.blockedOnlyBy(List.of()));
        events.record(new TaskImported(task.id(), oldHome.toString()), actor, now);
    }

    private Task withBlockersInEdi(Task task) {
        return task.blockedOnlyBy(task.intake().blockedBy().stream().filter(tasks::contains).toList());
    }

    private void saved(Decision decision) {
        decisions.save(decision);
        decisions.continueAfter(decision.number().value());
    }
}
