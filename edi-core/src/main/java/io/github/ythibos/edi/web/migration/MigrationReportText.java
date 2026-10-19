package io.github.ythibos.edi.web.migration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.migration.ArchiveSurvey;
import io.github.ythibos.edi.application.migration.BacklogSurvey;
import io.github.ythibos.edi.application.migration.DecisionSequenceSeed;
import io.github.ythibos.edi.application.migration.FileChange;
import io.github.ythibos.edi.application.migration.HomeChange;
import io.github.ythibos.edi.application.migration.HomeWriter;
import io.github.ythibos.edi.application.migration.MemoryRoute;
import io.github.ythibos.edi.application.migration.MigrationNote;
import io.github.ythibos.edi.application.migration.MigrationPlan;
import io.github.ythibos.edi.application.migration.MigrationReport;
import io.github.ythibos.edi.application.migration.SetAsideSection;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
public class MigrationReportText implements TextPresenter<MigrationReport> {

    private static final String INDENT = "  ";
    private static final String NONE = "none";
    private static final List<TaskState> STATES_IN_BACKLOG_ORDER = List.of(TaskState.IN_FLIGHT, TaskState.QUEUED, TaskState.DONE);
    private static final long KILOBYTE = 1024L;
    private static final long MEGABYTE = KILOBYTE * KILOBYTE;

    @Override
    public Class<MigrationReport> presents() {
        return MigrationReport.class;
    }

    @Override
    public String text(MigrationReport report) {
        MigrationPlan plan = report.plan();
        List<String> sections = new ArrayList<>();
        sections.add(openingOf(report));
        sections.add(tasksOf(plan.tasks(), report.backlog()));
        sections.add(holdsOf(plan.tasks()));
        sections.add(decisionsOf(plan.decisions()));
        sections.add(sequenceOf(plan.decisionSequence(), report.backlog().lastDecisionNumber()) + "\n"
                + ediKeysOf(plan.highestEdiKey(), report.backlog().upcomingEdiKey()) + "\n");
        plan.setAside().forEach(section -> sections.add(setAsideOf(section)));
        if (!plan.oldHarnessWork().isEmpty()) {
            sections.add(oldHarnessWorkOf(plan.oldHarnessWork()));
        }
        sections.add(homeOf(report));
        sections.add(memoryOf(plan.home().memory()));
        if (report.backlog().findsWorkOfEdisOwn()) {
            sections.add(ediOwnWorkOf(report.backlog()));
        }
        sections.add(notesOf(Stream.concat(plan.notes().stream(), report.backlog().conflicts().stream()).toList()));
        if (report.dryRun()) {
            sections.add(nextStepOf(report));
        }
        return String.join("\n", sections);
    }

    private static String openingOf(MigrationReport report) {
        return report.dryRun()
                ? "Dry run of the import from " + report.plan().source() + "; nothing was changed.\n"
                : "Imported from " + report.plan().source() + ".\n";
    }

    private static String tasksOf(List<Task> tasks, BacklogSurvey backlog) {
        Table table = new Table(INDENT).line(Words.counted("Tasks to import", tasks.size()));
        STATES_IN_BACKLOG_ORDER.forEach(state -> table.row(state.word(), countedWithHolds(tasks, state)));
        table.row("kinds", Arrays.stream(TaskKind.values())
                .map(kind -> kind.word() + " " + tasks.stream().filter(task -> task.intake().kind() == kind).count())
                .collect(Collectors.joining(", ")));
        if (!backlog.alreadyInEdi().isEmpty()) {
            table.row("already in EDI", backlog.alreadyInEdi().size() + ": " + idsOf(backlog.alreadyInEdi()));
        }
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

    private static String sequenceOf(DecisionSequenceSeed seed, int lastIssuedByEdi) {
        String sequence = "Decision sequence: " + seed.highest() + ", the highest in " + seed.source();
        if (lastIssuedByEdi > seed.highest()) {
            return sequence + "; EDI has already issued " + new DecisionNumber(lastIssuedByEdi) + ", so the first new decision is "
                    + new DecisionNumber(lastIssuedByEdi + 1) + ".";
        }
        return sequence + "; the first new decision is " + seed.firstNewNumber() + ".";
    }

    private static String ediKeysOf(WorkKey highest, WorkKey upcomingInEdi) {
        if (highest == null) {
            return "EDI work keys: none in use in the old home; the next is " + upcomingInEdi + ".";
        }
        WorkKey next = new WorkKey(highest.project(), Math.max(highest.number() + 1, upcomingInEdi.number()));
        return "EDI work keys: " + highest + " is the highest in use; the next is " + next + ".";
    }

    private static String setAsideOf(SetAsideSection section) {
        return new Table(INDENT)
                .line(Words.counted("Set aside, as the section \"" + section.section() + "\" holds no tasks", section.entries().size()))
                .line(INDENT + String.join(", ", section.entries()))
                .text();
    }

    private static String oldHarnessWorkOf(List<String> work) {
        return new Table(INDENT)
                .line(Words.counted("Set aside, as they improve the old harness EDI replaces", work.size()))
                .line(INDENT + String.join(", ", work))
                .text();
    }

    private static String homeOf(MigrationReport report) {
        Table table = new Table(INDENT).line("The EDI home at " + report.home().home());
        report.home().files().forEach(file -> table.row(file.path(), changeOf(file.change(), report.dryRun()), file.holds()));
        table.row(report.home().archive().path(), archiveChangeOf(report.home().archive(), report.dryRun()),
                report.home().archive().files() + " files of the old home, " + sizeOf(report.home().archive().bytes()));
        if (!report.home().links().isEmpty()) {
            table.row("data/tasks/*/firstofficer", linkChangesOf(report.home().links(), report.dryRun()),
                    "links to the old folders of open tasks");
        }
        if (!report.dryRun() && !report.home().replaced().isEmpty()) {
            table.line(INDENT + "Each replaced file is kept beside it as <file>" + HomeWriter.KEPT_SUFFIX + ".");
        }
        return table.text();
    }

    private static String memoryOf(List<MemoryRoute> routes) {
        Table table = new Table(INDENT).line(Words.counted("Memory sections", routes.size()));
        routes.forEach(route -> table.row(route.heading(), route.file()));
        return table.text();
    }

    private static String ediOwnWorkOf(BacklogSurvey backlog) {
        return new Table(INDENT)
                .line("EDI's own work, which the import leaves as it is")
                .row("tasks", backlog.ediOwnTasks().isEmpty() ? NONE : idsOf(backlog.ediOwnTasks()))
                .row("decisions", backlog.ediOwnDecisions().isEmpty() ? NONE
                        : backlog.ediOwnDecisions().stream().map(DecisionNumber::toString).collect(Collectors.joining(", ")))
                .text();
    }

    private static String notesOf(List<MigrationNote> notes) {
        Table table = new Table(INDENT).line(Words.counted("For the captain", notes.size()));
        notes.forEach(note -> table.row(note.subject(), note.note()));
        return table.text();
    }

    private static String nextStepOf(MigrationReport report) {
        String run = "edi migrate firstofficer --from " + report.plan().source();
        return report.meetsAHomeInUse()
                ? "This EDI home already holds work or files of its own; " + run + " --overwrite imports beside them, keeping each replaced file"
                        + " as <file>" + HomeWriter.KEPT_SUFFIX + ".\n"
                : run + " imports all of this.\n";
    }

    private static String countedWithHolds(List<Task> tasks, TaskState state) {
        List<Task> inState = tasks.stream().filter(task -> task.state() == state).toList();
        long held = inState.stream().filter(Task::isHeld).count();
        return inState.size() + (held == 0 ? "" : " (" + held + " held)");
    }

    private static String idsOf(List<TaskId> tasks) {
        return tasks.stream().map(TaskId::value).collect(Collectors.joining(", "));
    }

    private static String numbersOf(List<Decision> decisions) {
        return decisions.isEmpty()
                ? NONE
                : decisions.stream()
                        .sorted(Comparator.comparingInt(decision -> decision.number().value()))
                        .map(decision -> decision.number().toString())
                        .collect(Collectors.joining(", "));
    }

    private static String changeOf(HomeChange change, boolean dryRun) {
        return switch (change) {
            case ADDED -> dryRun ? "to add" : "added";
            case UNCHANGED -> "unchanged";
            case REPLACED -> dryRun ? "to replace" : "replaced";
        };
    }

    private static String archiveChangeOf(ArchiveSurvey archive, boolean dryRun) {
        List<String> parts = new ArrayList<>();
        if (archive.added() > 0) {
            parts.add(archive.added() + " " + changeOf(HomeChange.ADDED, dryRun));
        }
        if (archive.replaced() > 0) {
            parts.add(archive.replaced() + " " + changeOf(HomeChange.REPLACED, dryRun));
        }
        if (archive.unchanged() > 0 || parts.isEmpty()) {
            parts.add(archive.unchanged() + " " + changeOf(HomeChange.UNCHANGED, dryRun));
        }
        return String.join(", ", parts);
    }

    private static String sizeOf(long bytes) {
        return bytes < MEGABYTE
                ? Math.ceilDiv(bytes, KILOBYTE) + " kB"
                : String.format(Locale.ROOT, "%.1f MB", (double) bytes / MEGABYTE);
    }

    private static String linkChangesOf(List<FileChange> links, boolean dryRun) {
        return Arrays.stream(HomeChange.values())
                .filter(change -> links.stream().anyMatch(link -> link.change() == change))
                .map(change -> links.stream().filter(link -> link.change() == change).count() + " " + changeOf(change, dryRun))
                .collect(Collectors.joining(", "));
    }
}
