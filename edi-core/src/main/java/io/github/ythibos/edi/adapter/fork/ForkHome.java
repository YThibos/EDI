package io.github.ythibos.edi.adapter.fork;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.AsciiCharacters;

public record ForkHome(Path root, String backlog, String decisionSequence, String doneArchive, String noteArchive) {

    public static final String HARNESS_PROJECT = "firstofficer";
    private static final String BACKLOG = "data/backlog.md";
    private static final String DECISION_SEQUENCE = "data/decision-seq";
    private static final String DONE_ARCHIVE = "data/done-archive.md";
    private static final String NOTE_ARCHIVE = "data/note-archive.md";
    private static final String PROJECT_REGISTRY = "data/projects.md";
    private static final String CAPTAIN_MEMORY = "data/captain.md";
    private static final String SHARED_CAPTAIN_MEMORY = "data/captain-shared.md";
    private static final String LEARNINGS = "data/learnings.md";
    private static final String MEMORY_ARCHIVE = "data/memory-archive.md";
    private static final String ROUTING_RULES = "config/crew-dispatch.json";
    private static final String CRAFT_RULES_PROJECTS = "config/craft-rules-projects";
    private static final String CRAFT_RULES_SCRIPT = "bin/fm-craft-rules.sh";
    private static final String FLEET_LEDGER_SWITCH = "config/fleet-ledger";
    private static final String STARTUP_MEMORY_BUDGET = "config/startup-memory-budget";
    private static final String PROJECT_CLONES = "projects";
    private static final String COMMENT = "#";
    private static final String DECISION_PREFIX = "D";
    private static final String KEY_SEPARATOR = "-";
    private static final int MOST_DIGITS = 9;

    public static ForkHome at(Path root) {
        String backlog = present(root.resolve(BACKLOG))
                .orElseThrow(() -> new IllegalArgumentException(root + " is not a firstofficer home: it has no " + BACKLOG + "."));
        return new ForkHome(
                root,
                backlog,
                present(root.resolve(DECISION_SEQUENCE)).orElse(""),
                present(root.resolve(DONE_ARCHIVE)).orElse(""),
                present(root.resolve(NOTE_ARCHIVE)).orElse(""));
    }

    public static Optional<Path> holdingTheClone(Path clone) {
        Path clones = clone.getParent();
        if (clones == null || clones.getParent() == null || !clones.getFileName().toString().equals(PROJECT_CLONES)) {
            return Optional.empty();
        }
        return Optional.of(clones.getParent()).filter(root -> Files.isRegularFile(root.resolve(BACKLOG)));
    }

    public static List<ArchivedFile> archiveOf(Path root) {
        return ForkArchive.of(root);
    }

    public SeenNumber highestDecisionNumber() {
        SeenNumber recorded = new SeenNumber(recordedDecisionSequence(), DECISION_SEQUENCE);
        return highestOf(recorded, DECISION_PREFIX);
    }

    public SeenNumber highestWorkKeyNumber(String projectKey) {
        return highestOf(new SeenNumber(0, BACKLOG), projectKey + KEY_SEPARATOR);
    }

    public List<ForkProject> projects() {
        return text(PROJECT_REGISTRY).map(ForkProject::allIn).orElse(List.of());
    }

    public Path projectClone(String project) {
        return root.resolve(PROJECT_CLONES).resolve(project);
    }

    public Optional<String> captainMemory() {
        return text(CAPTAIN_MEMORY);
    }

    public Optional<String> sharedCaptainMemory() {
        return text(SHARED_CAPTAIN_MEMORY);
    }

    public Optional<String> learnings() {
        return text(LEARNINGS);
    }

    public Optional<String> memoryArchive() {
        return text(MEMORY_ARCHIVE);
    }

    public Optional<String> routingRules() {
        return text(ROUTING_RULES);
    }

    public List<String> craftRulesProjects() {
        return text(CRAFT_RULES_PROJECTS).stream()
                .flatMap(String::lines)
                .map(ForkHome::withoutComment)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    public Optional<String> craftRules() {
        return text(CRAFT_RULES_SCRIPT).flatMap(CraftRulesScript::printedRules);
    }

    public boolean keepsAFleetLedger() {
        return Files.exists(root.resolve(FLEET_LEDGER_SWITCH));
    }

    public OptionalInt startupMemoryBudget() {
        String budget = text(STARTUP_MEMORY_BUDGET).orElse("").strip();
        return budget.length() <= MOST_DIGITS && AsciiCharacters.isDigits(budget) ? OptionalInt.of(Integer.parseInt(budget)) : OptionalInt.empty();
    }

    private static Optional<String> present(Path file) {
        try {
            return Optional.of(Files.readString(file));
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private int recordedDecisionSequence() {
        String recorded = decisionSequence.strip();
        return recorded.length() <= MOST_DIGITS && AsciiCharacters.isDigits(recorded) ? Integer.parseInt(recorded) : 0;
    }

    private SeenNumber highestOf(SeenNumber start, String prefix) {
        return Stream.of(
                        new SeenNumber(NumbersInText.highestAfter(prefix, backlog), BACKLOG),
                        new SeenNumber(NumbersInText.highestAfter(prefix, doneArchive), DONE_ARCHIVE),
                        new SeenNumber(NumbersInText.highestAfter(prefix, noteArchive), NOTE_ARCHIVE))
                .reduce(start, (highest, seen) -> seen.value() > highest.value() ? seen : highest);
    }

    private Optional<String> text(String file) {
        return present(root.resolve(file));
    }

    private static String withoutComment(String line) {
        int comment = line.indexOf(COMMENT);
        return (comment < 0 ? line : line.substring(0, comment)).strip();
    }
}
