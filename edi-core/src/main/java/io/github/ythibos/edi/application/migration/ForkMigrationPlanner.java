package io.github.ythibos.edi.application.migration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.github.ythibos.edi.adapter.fork.ForkBacklog;
import io.github.ythibos.edi.adapter.fork.ForkBacklogReader;
import io.github.ythibos.edi.adapter.fork.ForkEntry;
import io.github.ythibos.edi.adapter.fork.ForkHome;
import io.github.ythibos.edi.adapter.fork.SeenNumber;
import io.github.ythibos.edi.adapter.fork.SetAsideEntry;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class ForkMigrationPlanner {

    private static final ProjectKey EDI = new ProjectKey("EDI");
    private static final int LONGEST_QUOTED_LINE = 80;

    private final ForkBacklogReader reader;
    private final HomeImportPlanner homeImports;
    private final Clock clock;

    public ForkMigrationPlanner(ForkBacklogReader reader, HomeImportPlanner homeImports, Clock clock) {
        this.reader = reader;
        this.homeImports = homeImports;
        this.clock = clock;
    }

    public MigrationPlan plan(Path oldHome) {
        ForkHome home = ForkHome.at(oldHome);
        MigrationNotes notes = new MigrationNotes();
        MappedBacklog backlog = mapped(home, notes);
        HomeImport homeImport = homeImports.planFor(home, backlog.tasks(), notes);
        return new MigrationPlan(
                oldHome,
                backlog.tasks(),
                backlog.migrated().stream().map(MigratedEntry::decision).filter(Objects::nonNull).toList(),
                seedOf(home.highestDecisionNumber()),
                highestEdiKeyIn(home),
                setAsideIn(backlog.read()),
                backlog.oldHarnessWork(),
                homeImport,
                notes.all());
    }

    public MigratedEntry entryOf(Path oldHome, TaskId task) {
        MigrationNotes notes = new MigrationNotes();
        MappedBacklog backlog = mapped(ForkHome.at(oldHome), notes);
        if (backlog.oldHarnessWork().contains(task.value())) {
            throw new Refusal(task + " improves the old harness itself, which EDI replaces, so EDI does not take it over.");
        }
        return backlog.migrated().stream()
                .filter(entry -> entry.task().id().equals(task))
                .findFirst()
                .orElseThrow(() -> new Refusal(task + " is not a task EDI imports from " + oldHome + whyNot(task, notes) + "."));
    }

    private MappedBacklog mapped(ForkHome home, MigrationNotes notes) {
        ForkBacklog backlog = reader.read(home.backlog());
        List<ForkEntry> firstEntries = firstEntryOfEachId(backlog, notes);
        List<ForkEntry> carried = firstEntries.stream().filter(entry -> !ForkEntryMapping.improvesTheOldHarness(entry)).toList();
        ForkEntryMapping mapping = new ForkEntryMapping(idsIn(backlog), importableIdsIn(carried), clock.now(), notes);
        List<MigratedEntry> migrated = carried.stream().map(mapping::migrated).flatMap(Optional::stream).toList();
        noteStrayLines(backlog, notes);
        return new MappedBacklog(backlog, migrated,
                firstEntries.stream().filter(ForkEntryMapping::improvesTheOldHarness).map(ForkEntry::id).toList());
    }

    private static DecisionSequenceSeed seedOf(SeenNumber highest) {
        return new DecisionSequenceSeed(highest.value(), highest.source());
    }

    private static WorkKey highestEdiKeyIn(ForkHome home) {
        int highest = home.highestWorkKeyNumber(EDI.value()).value();
        return highest == 0 ? null : new WorkKey(EDI, highest);
    }

    private static List<SetAsideSection> setAsideIn(ForkBacklog backlog) {
        Map<String, List<String>> bySection = backlog.setAside().stream().collect(Collectors.groupingBy(
                SetAsideEntry::section, LinkedHashMap::new, Collectors.mapping(SetAsideEntry::id, Collectors.toList())));
        return bySection.entrySet().stream().map(section -> new SetAsideSection(section.getKey(), section.getValue())).toList();
    }

    private static String whyNot(TaskId task, MigrationNotes notes) {
        List<String> reasons = notes.all().stream().filter(note -> note.subject().equals(task.value())).map(MigrationNote::note).toList();
        return reasons.isEmpty() ? ": its backlog holds no open entry of that name" : ": it " + String.join("; it ", reasons);
    }

    private static List<ForkEntry> firstEntryOfEachId(ForkBacklog backlog, MigrationNotes notes) {
        Set<String> seen = new HashSet<>();
        List<ForkEntry> firstEntries = new ArrayList<>();
        for (ForkEntry entry : backlog.entries()) {
            if (seen.add(entry.id())) {
                firstEntries.add(entry);
            } else {
                notes.add(entry.id(), "appears again on line " + entry.line() + " of the backlog; only its first entry is imported");
            }
        }
        return firstEntries;
    }

    private static Set<String> idsIn(ForkBacklog backlog) {
        return backlog.entries().stream().map(ForkEntry::id).collect(Collectors.toSet());
    }

    private static Set<String> importableIdsIn(List<ForkEntry> entries) {
        return entries.stream().filter(ForkEntryMapping::importable).map(ForkEntry::id).collect(Collectors.toSet());
    }

    private static void noteStrayLines(ForkBacklog backlog, MigrationNotes notes) {
        backlog.strayLines().forEach(stray -> notes.add(
                "Backlog line " + stray.line(),
                "is not a task entry and is not imported: " + shortened(stray.text())));
    }

    private static String shortened(String line) {
        String text = line.strip();
        return text.length() <= LONGEST_QUOTED_LINE ? text : text.substring(0, LONGEST_QUOTED_LINE) + "...";
    }

    private record MappedBacklog(ForkBacklog read, List<MigratedEntry> migrated, List<String> oldHarnessWork) {

        List<Task> tasks() {
            return migrated.stream().map(MigratedEntry::task).toList();
        }
    }
}
