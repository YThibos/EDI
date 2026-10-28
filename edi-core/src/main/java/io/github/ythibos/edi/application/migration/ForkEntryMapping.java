package io.github.ythibos.edi.application.migration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.github.ythibos.edi.adapter.fork.ForkDependency;
import io.github.ythibos.edi.adapter.fork.ForkEntry;
import io.github.ythibos.edi.adapter.fork.ForkHold;
import io.github.ythibos.edi.adapter.fork.ForkHome;
import io.github.ythibos.edi.adapter.fork.ForkSection;
import io.github.ythibos.edi.adapter.fork.ForkTags;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;

final class ForkEntryMapping {

    private static final String KIND_OF_AN_UNMARKED_ENTRY = "task";
    private static final Map<String, TaskKind> KINDS = Map.of(
            "ship", TaskKind.SHIP,
            "task", TaskKind.SHIP,
            "docs", TaskKind.SHIP,
            "scout", TaskKind.SCOUT,
            "captain", TaskKind.CAPTAIN);
    private static final String NO_PROJECT = "-";
    private static final int LONGEST_QUOTED_REASON = 80;

    private final Set<String> backlogIds;
    private final Set<String> importableIds;
    private final Instant migratedAt;
    private final MigrationNotes notes;
    private final ForkDecisionMapping decisions;

    ForkEntryMapping(Set<String> backlogIds, Set<String> importableIds, Instant migratedAt, MigrationNotes notes) {
        this.backlogIds = Set.copyOf(backlogIds);
        this.importableIds = Set.copyOf(importableIds);
        this.migratedAt = migratedAt;
        this.notes = notes;
        this.decisions = new ForkDecisionMapping(notes);
    }

    static boolean importable(ForkEntry entry) {
        return TaskId.isWritten(entry.id()) && KINDS.containsKey(kindWordOf(entry.tags().kind()));
    }

    static boolean improvesTheOldHarness(ForkEntry entry) {
        List<String> projects = projectNamesOf(entry);
        return !projects.isEmpty() && projects.stream().allMatch(ForkHome.HARNESS_PROJECT::equals);
    }

    Optional<MigratedEntry> migrated(ForkEntry entry) {
        if (!TaskId.isWritten(entry.id())) {
            notes.add(entry.id(), "is not imported: I name a task in at most 64 lower-case letters, digits and dashes, such as fix-login-timeout");
            return Optional.empty();
        }
        try {
            TaskId id = new TaskId(entry.id());
            Decision decision = decisionOf(entry, id).orElse(null);
            return Optional.of(new MigratedEntry(taskOf(entry, id, decision), decision));
        } catch (IllegalArgumentException unmappable) {
            notes.add(entry.id(), "is not imported: " + unmappable.getMessage());
            return Optional.empty();
        }
    }

    private static String kindWordOf(String forkKind) {
        return forkKind == null ? KIND_OF_AN_UNMARKED_ENTRY : forkKind;
    }

    private Optional<Decision> decisionOf(ForkEntry entry, TaskId id) {
        if (entry.decision() == null) {
            return Optional.empty();
        }
        Optional<Decision> raised = decisions.raisedDecision(entry.decision(), id, heldSince(entry));
        if (awaitsTheCaptain(entry)) {
            return raised;
        }
        return raised.flatMap(decision -> decisions.answered(decision, entry.decision().recordedAnswer(), answeredAt(entry)));
    }

    private Task taskOf(ForkEntry entry, TaskId id, Decision decision) {
        TaskState state = stateOf(entry.section());
        return new Task(
                id,
                intakeOf(entry, id),
                state,
                String.join("\n", entry.body()),
                state == TaskState.DONE ? null : holdOf(entry, id, decision),
                createdAt(entry),
                state == TaskState.DONE ? closedAt(entry) : null);
    }

    private Instant heldSince(ForkEntry entry) {
        return entry.holdSet() != null ? entry.holdSet() : startOf(entry.tags().since(), createdAt(entry));
    }

    private static boolean awaitsTheCaptain(ForkEntry entry) {
        ForkHold hold = entry.tags().hold();
        return entry.section() != ForkSection.DONE && hold != null && hold.isForTheCaptain();
    }

    private Instant answeredAt(ForkEntry entry) {
        return startOf(entry.tags().closed(), heldSince(entry));
    }

    private static TaskState stateOf(ForkSection section) {
        return switch (section) {
            case IN_FLIGHT -> TaskState.IN_FLIGHT;
            case QUEUED -> TaskState.QUEUED;
            case DONE -> TaskState.DONE;
        };
    }

    private Intake intakeOf(ForkEntry entry, TaskId id) {
        ForkTags tags = entry.tags();
        return new Intake(kindOf(tags.kind()), tags.title(), projectsOf(entry, id), workKeyOpening(tags.title()), null, blockersOf(entry, id),
                null);
    }

    private CaptainHold holdOf(ForkEntry entry, TaskId id, Decision decision) {
        ForkHold hold = entry.tags().hold();
        if (hold == null) {
            return null;
        }
        if (decision != null && decision.isOpen()) {
            return decision.holdOnItsTask();
        }
        if (!hold.isForTheCaptain()) {
            notes.add(id.value(), "waits" + untilWords(hold) + " on something outside me (\"" + shortened(hold.reason())
                    + "\"); I hold a task only for the captain, so the wait is not carried");
            return null;
        }
        return new CaptainHold(HoldKind.DEFERRAL, hold.reason(), untilOf(hold), heldSince(entry));
    }

    private Instant createdAt(ForkEntry entry) {
        LocalDate since = entry.tags().since();
        return startOf(since != null ? since : entry.tags().closed(), migratedAt);
    }

    private Instant closedAt(ForkEntry entry) {
        return startOf(entry.tags().closed(), createdAt(entry));
    }

    private static Instant startOf(LocalDate day, Instant otherwise) {
        return day == null ? otherwise : day.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static TaskKind kindOf(String forkKind) {
        String kind = kindWordOf(forkKind);
        if (!KINDS.containsKey(kind)) {
            throw new IllegalArgumentException("its kind " + kind + " has no counterpart of mine");
        }
        return KINDS.get(kind);
    }

    private List<ProjectName> projectsOf(ForkEntry entry, TaskId id) {
        List<String> named = projectNamesOf(entry);
        if (named.isEmpty()) {
            notes.add(id.value(), "names no project; the captain sets one");
        }
        if (named.contains(ForkHome.HARNESS_PROJECT)) {
            notes.add(id.value(), "also names the old harness itself; I keep only its other projects");
        }
        return named.stream().filter(name -> !name.equals(ForkHome.HARNESS_PROJECT)).flatMap(name -> projectNamed(name, id)).distinct().toList();
    }

    private static WorkKey workKeyOpening(String title) {
        try {
            return WorkKey.parse(title.split("\\s", 2)[0]);
        } catch (IllegalArgumentException noKey) {
            return null;
        }
    }

    private List<TaskId> blockersOf(ForkEntry entry, TaskId id) {
        return entry.tags().dependencies().stream()
                .filter(dependency -> carried(dependency, id))
                .map(dependency -> new TaskId(dependency.id()))
                .distinct()
                .toList();
    }

    private static String untilWords(ForkHold hold) {
        return hold.until() == null ? "" : " until " + hold.until();
    }

    private static String shortened(String reason) {
        String text = reason.strip();
        return text.length() <= LONGEST_QUOTED_REASON ? text : text.substring(0, LONGEST_QUOTED_REASON) + "...";
    }

    private static HoldUntil untilOf(ForkHold hold) {
        return hold.until() == null ? HoldUntil.captainsWord() : HoldUntil.day(hold.until());
    }

    static List<String> projectNamesOf(ForkEntry entry) {
        List<String> cardProjects = entry.decision() == null || entry.decision().card() == null
                ? List.of()
                : entry.decision().card().projects();
        if (!cardProjects.isEmpty()) {
            return cardProjects;
        }
        String repo = entry.tags().repo();
        return repo == null || repo.equals(NO_PROJECT) ? List.of() : List.of(repo);
    }

    private Stream<ProjectName> projectNamed(String name, TaskId id) {
        try {
            return Stream.of(new ProjectName(name));
        } catch (IllegalArgumentException unusable) {
            notes.add(id.value(), "names the project \"" + name + "\", which is not a project name I accept; it is dropped");
            return Stream.empty();
        }
    }

    private boolean carried(ForkDependency dependency, TaskId id) {
        String unless = reasonNotToCarry(dependency, id);
        if (unless != null) {
            notes.add(id.value(), unless);
        }
        return unless == null;
    }

    private String reasonNotToCarry(ForkDependency dependency, TaskId id) {
        if (!dependency.blocks()) {
            return "has a " + dependency.type() + " link to " + dependency.id() + ", which I do not carry";
        }
        if (dependency.id().equals(id.value())) {
            return "is blocked by itself; the dependency is dropped";
        }
        if (!backlogIds.contains(dependency.id())) {
            return "is blocked by " + dependency.id() + ", which is not in the backlog; the dependency is dropped";
        }
        return importableIds.contains(dependency.id())
                ? null
                : "is blocked by " + dependency.id() + ", which is not imported; the dependency is dropped";
    }
}
