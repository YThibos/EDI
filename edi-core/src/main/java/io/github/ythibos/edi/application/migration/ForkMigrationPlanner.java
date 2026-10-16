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
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;

@Service
public class ForkMigrationPlanner {

    private static final ProjectKey EDI = new ProjectKey("EDI");
    private static final int LONGEST_QUOTED_LINE = 80;

    private final ForkBacklogReader reader;
    private final Clock clock;

    public ForkMigrationPlanner(ForkBacklogReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    public MigrationPlan plan(Path forkHome) {
        ForkHome home = ForkHome.at(forkHome);
        ForkBacklog backlog = reader.read(home.backlog());
        MigrationNotes notes = new MigrationNotes();
        List<ForkEntry> firstEntries = firstEntryOfEachId(backlog, notes);
        ForkEntryMapping mapping = new ForkEntryMapping(idsIn(backlog), importableIdsIn(firstEntries), clock.now(), notes);
        List<MigratedEntry> migrated = firstEntries.stream().map(mapping::migrated).flatMap(Optional::stream).toList();
        noteStrayLines(backlog, notes);
        return new MigrationPlan(
                forkHome,
                migrated.stream().map(MigratedEntry::task).toList(),
                migrated.stream().map(MigratedEntry::decision).filter(Objects::nonNull).toList(),
                seedOf(home.highestDecisionNumber()),
                highestEdiKeyIn(backlog),
                setAsideIn(backlog),
                notes.all());
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

    private static DecisionSequenceSeed seedOf(SeenNumber highest) {
        return new DecisionSequenceSeed(highest.value(), highest.source());
    }

    private static WorkKey highestEdiKeyIn(ForkBacklog backlog) {
        int highest = backlog.highestWorkKeyNumber(EDI.value());
        return highest == 0 ? null : new WorkKey(EDI, highest);
    }

    private static List<SetAsideSection> setAsideIn(ForkBacklog backlog) {
        Map<String, List<String>> bySection = backlog.setAside().stream().collect(Collectors.groupingBy(
                SetAsideEntry::section, LinkedHashMap::new, Collectors.mapping(SetAsideEntry::id, Collectors.toList())));
        return bySection.entrySet().stream().map(section -> new SetAsideSection(section.getKey(), section.getValue())).toList();
    }

    private static String shortened(String line) {
        String text = line.strip();
        return text.length() <= LONGEST_QUOTED_LINE ? text : text.substring(0, LONGEST_QUOTED_LINE) + "...";
    }
}
