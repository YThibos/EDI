package io.github.ythibos.edi.web.migration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.migration.DecisionSequenceSeed;
import io.github.ythibos.edi.application.migration.MigrationNote;
import io.github.ythibos.edi.application.migration.MigrationPlan;
import io.github.ythibos.edi.application.migration.SetAsideSection;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
public class MigrationPlanText implements TextPresenter<MigrationPlan> {

    private static final String INDENT = "  ";
    private static final String NONE = "none";
    private static final List<TaskState> STATES_IN_BACKLOG_ORDER = List.of(TaskState.IN_FLIGHT, TaskState.QUEUED, TaskState.DONE);

    @Override
    public Class<MigrationPlan> presents() {
        return MigrationPlan.class;
    }

    @Override
    public String text(MigrationPlan plan) {
        List<String> sections = new ArrayList<>();
        sections.add("Dry run of the import from " + plan.source() + "; nothing was changed.\n");
        sections.add(tasksOf(plan.tasks()));
        sections.add(holdsOf(plan.tasks()));
        sections.add(decisionsOf(plan.decisions()));
        sections.add(sequenceOf(plan.decisionSequence()) + "\n" + ediKeysOf(plan.highestEdiKey()) + "\n");
        plan.setAside().forEach(section -> sections.add(setAsideOf(section)));
        sections.add(notesOf(plan.notes()));
        return String.join("\n", sections);
    }

    private static String tasksOf(List<Task> tasks) {
        Table table = new Table(INDENT).line(Words.counted("Tasks to import", tasks.size()));
        STATES_IN_BACKLOG_ORDER.forEach(state -> table.row(state.word(), countedWithHolds(tasks, state)));
        table.row("kinds", Arrays.stream(TaskKind.values())
                .map(kind -> kind.word() + " " + tasks.stream().filter(task -> task.intake().kind() == kind).count())
                .collect(Collectors.joining(", ")));
        return table.text();
    }

    private static String holdsOf(List<Task> tasks) {
        List<Task> held = tasks.stream().filter(Task::isHeld).toList();
        Table table = new Table(INDENT).line(Words.counted("Captain holds", held.size()));
        held.forEach(task -> table.row(task.id().value(), task.state().word(), task.hold().kind().word() + ", " + Moments.until(task.hold().until())));
        return table.text();
    }

    private static String decisionsOf(List<Decision> decisions) {
        List<Decision> open = decisions.stream().filter(Decision::isOpen).toList();
        List<Decision> answered = decisions.stream().filter(decision -> !decision.isOpen()).toList();
        return new Table(INDENT)
                .line("Decisions: " + open.size() + " open, " + answered.size() + " answered")
                .row("open", numbersOf(open))
                .row("answered", numbersOf(answered))
                .text();
    }

    private static String sequenceOf(DecisionSequenceSeed seed) {
        return "Decision sequence: " + seed.highest() + ", the highest in " + seed.source() + "; the first new decision is " + seed.firstNewNumber() + ".";
    }

    private static String ediKeysOf(WorkKey highest) {
        return highest == null
                ? "EDI work keys: none in use; the first is EDI-1."
                : "EDI work keys: " + highest + " is the highest in use; the next is " + new WorkKey(highest.project(), highest.number() + 1) + ".";
    }

    private static String setAsideOf(SetAsideSection section) {
        return new Table(INDENT)
                .line(Words.counted("Set aside, as the section \"" + section.section() + "\" holds no tasks", section.entries().size()))
                .line(INDENT + String.join(", ", section.entries()))
                .text();
    }

    private static String notesOf(List<MigrationNote> notes) {
        Table table = new Table(INDENT).line(Words.counted("For the captain", notes.size()));
        notes.forEach(note -> table.row(note.subject(), note.note()));
        return table.text();
    }

    private static String countedWithHolds(List<Task> tasks, TaskState state) {
        List<Task> inState = tasks.stream().filter(task -> task.state() == state).toList();
        long held = inState.stream().filter(Task::isHeld).count();
        return inState.size() + (held == 0 ? "" : " (" + held + " held)");
    }

    private static String numbersOf(List<Decision> decisions) {
        return decisions.isEmpty()
                ? NONE
                : decisions.stream()
                        .sorted(Comparator.comparingInt(decision -> decision.number().value()))
                        .map(decision -> decision.number().toString())
                        .collect(Collectors.joining(", "));
    }
}
