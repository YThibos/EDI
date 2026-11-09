package io.github.ythibos.edi.application.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.fork.ForkHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.runtime.ClaudeSessions;
import io.github.ythibos.edi.adapter.store.AdoptionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.dispatch.IsolatedCopies;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdopted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskKeyed;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.Adoption;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class AdoptForkTask {

    private static final String GIT_LINK = ".git";

    private final EdiHome home;
    private final ForkMigrationPlanner planner;
    private final BacklogImport backlog;
    private final TaskStore tasks;
    private final ProjectRegistry registry;
    private final IsolatedCopies copies;
    private final AdoptionStore adoptions;
    private final ClaudeSessions sessions;
    private final EventLog events;
    private final Git git;
    private final ProcessTable processes;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AdoptForkTask(EdiHome home, ForkMigrationPlanner planner, BacklogImport backlog, TaskStore tasks, ProjectRegistry registry,
            IsolatedCopies copies, AdoptionStore adoptions, ClaudeSessions sessions, EventLog events, Git git, ProcessTable processes, Clock clock,
            TransactionTemplate transaction) {
        this.home = home;
        this.planner = planner;
        this.backlog = backlog;
        this.tasks = tasks;
        this.registry = registry;
        this.copies = copies;
        this.adoptions = adoptions;
        this.sessions = sessions;
        this.events = events;
        this.git = git;
        this.processes = processes;
        this.clock = clock;
        this.transaction = transaction;
    }

    public AdoptedTask adopt(TaskId id, Path oldHome, BranchName branch, Path oldCopy, UUID session, Actor actor) {
        Path from = oldHome == null ? oldHomeHolding(oldCopy) : oldHome;
        MigratedEntry entry = keyedByItsBranch(planner.entryOf(from, id), branch);
        requireTheSameKeyInEdi(entry.task().id(), branch);
        ProjectEntry project = registry.entryOf(onlyProjectOf(entry.task()));
        Optional<Path> transcript = transcriptOf(session);
        RegisteredCopy registered = registeredCopyOf(project, entry.task(), branch, oldCopy);
        transcript.ifPresent(found -> sessions.carryInto(found, registered.copy()));
        transaction.executeWithoutResult(_ -> recordedAdoption(entry, from, branch, oldCopy, session, actor));
        return new AdoptedTask(id, branch, registered.copy(), git.headSummary(registered.copy()), oldCopy, registered.moved(), session);
    }

    private Path oldHomeHolding(Path oldCopy) {
        if (!git.isRepository(oldCopy)) {
            throw new Refusal(Rule.ADOPTABLE, oldCopy + " is not a git copy, so it names no old home; name the old home with --from.");
        }
        return ForkHome.holdingTheClone(git.commonDirectory(oldCopy).getParent())
                .orElseThrow(() -> new Refusal(Rule.ADOPTABLE, oldCopy + " is not a copy of a clone in a firstofficer home; name the old home with --from."));
    }

    private static MigratedEntry keyedByItsBranch(MigratedEntry entry, BranchName branch) {
        Task task = entry.task();
        if (task.isClosed()) {
            throw new Refusal(Rule.ADOPTABLE, task.id() + " is " + task.state().word() + " in the old home, and only open work is taken over.");
        }
        if (task.intake().workKey() == null) {
            return new MigratedEntry(task.keyedOn(branch.workKey()), entry.decision());
        }
        requireTheBranchKey(task, branch);
        return entry;
    }

    private void requireTheSameKeyInEdi(TaskId id, BranchName branch) {
        transaction.executeWithoutResult(_ -> {
            if (tasks.contains(id) && tasks.get(id).intake().workKey() != null) {
                requireTheBranchKey(tasks.get(id), branch);
            }
        });
    }

    private static ProjectName onlyProjectOf(Task task) {
        List<ProjectName> projects = task.intake().projects();
        if (projects.size() != 1) {
            throw new Refusal(Rule.ONE_PROJECT, task.id() + " works on " + projects.size() + " projects, and I take over the isolated copy of exactly one.");
        }
        return projects.getFirst();
    }

    private Optional<Path> transcriptOf(UUID session) {
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(sessions.transcriptOf(session).orElseThrow(() -> new Refusal(Rule.ADOPTABLE, "No Claude Code transcript of session " + session
                + " exists on this machine, so I could not resume it; adopt without --session to start the next worker afresh.")));
    }

    private RegisteredCopy registeredCopyOf(ProjectEntry project, Task task, BranchName branch, Path oldCopy) {
        Path copy = home.worktreeOf(project.project().name(), task.id());
        if (movedEarlier(copy, oldCopy)) {
            return new RegisteredCopy(prepared(project, task, branch), true);
        }
        requireTheBranchIn(oldCopy, branch);
        if (!processes.workingIn(oldCopy).isEmpty()) {
            linkInPlace(copy, oldCopy);
            return new RegisteredCopy(copy, false);
        }
        moveUnderEdi(project, branch, oldCopy, copy);
        return new RegisteredCopy(prepared(project, task, branch), true);
    }

    private void recordedAdoption(MigratedEntry entry, Path oldHome, BranchName branch, Path oldCopy, UUID session, Actor actor) {
        backlog.importInto(List.of(entry), oldHome, actor);
        TaskId task = entry.task().id();
        Instant now = clock.now();
        keyedOnTheBranch(task, branch, now, actor);
        if (adoptions.of(task).filter(earlier -> earlier.continues(branch, session)).isPresent()) {
            return;
        }
        adoptions.save(new Adoption(task, branch, session, now));
        events.record(new TaskAdopted(task, branch, oldCopy.toString(), session), actor, now);
    }

    private static void requireTheBranchKey(Task task, BranchName branch) {
        if (!task.intake().workKey().equals(branch.workKey())) {
            throw new Refusal(Rule.WORK_KEY, task.id() + " books its work on " + task.intake().workKey() + ", but " + branch + " carries "
                    + branch.workKey() + ".");
        }
    }

    private static boolean movedEarlier(Path copy, Path oldCopy) {
        return !Files.exists(oldCopy, LinkOption.NOFOLLOW_LINKS) && Files.isDirectory(copy, LinkOption.NOFOLLOW_LINKS);
    }

    private Path prepared(ProjectEntry project, Task task, BranchName branch) {
        BaseBranch base = task.intake().base() == null ? project.requireBase() : task.intake().base();
        return copies.prepare(project, task.id(), branch, base);
    }

    private void requireTheBranchIn(Path oldCopy, BranchName branch) {
        if (!git.isRepository(oldCopy)) {
            throw new Refusal(Rule.ADOPTABLE, oldCopy + " is not a git copy, so it holds no branch to take over.");
        }
        if (git.gitDirectory(oldCopy).equals(git.commonDirectory(oldCopy))) {
            throw new Refusal(Rule.ADOPTABLE, oldCopy
                    + " is the primary directory of a clone, not an isolated copy; name the copy that holds the task's branch.");
        }
        String checkedOut = git.currentBranch(oldCopy);
        if (!checkedOut.equals(branch.toString())) {
            throw new Refusal(Rule.ADOPTABLE, oldCopy + " has " + checkedOut + " checked out, not " + branch + "; name the copy that holds the task's branch.");
        }
    }

    private static void linkInPlace(Path copy, Path oldCopy) {
        if (Files.isSymbolicLink(copy) && linkTargetOf(copy).equals(oldCopy)) {
            return;
        }
        requireNoCopyAt(copy);
        try {
            Files.createDirectories(copy.getParent());
            Files.createSymbolicLink(copy, oldCopy);
        } catch (IOException unlinked) {
            throw new UncheckedIOException(unlinked);
        }
    }

    private void moveUnderEdi(ProjectEntry project, BranchName branch, Path oldCopy, Path copy) {
        unlinkTheRegistrationOf(copy, oldCopy);
        requireNoCopyAt(copy);
        copies.requireAClone(project);
        fetchTheBranch(project, branch, oldCopy);
        createParentOf(copy);
        git.addWorktreeOnBranchWithoutCheckout(project.projectClone(), copy, branch);
        moveTheWorkingTree(oldCopy, copy);
        git.indexTheHeadKeepingTheWorkingTree(copy);
    }

    private void keyedOnTheBranch(TaskId id, BranchName branch, Instant now, Actor actor) {
        Task task = tasks.get(id);
        if (task.intake().workKey() == null) {
            WorkKey key = branch.workKey();
            tasks.update(task.keyedOn(key));
            events.record(new TaskKeyed(id, key), actor, now);
        }
    }

    private static Path linkTargetOf(Path link) {
        try {
            return Files.readSymbolicLink(link);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void requireNoCopyAt(Path copy) {
        if (Files.exists(copy, LinkOption.NOFOLLOW_LINKS)) {
            throw new Refusal(Rule.ADOPTABLE, "I already hold an isolated copy at " + copy + ", so the old copy cannot take its place.");
        }
    }

    private static void unlinkTheRegistrationOf(Path copy, Path oldCopy) {
        try {
            if (Files.isSymbolicLink(copy) && linkTargetOf(copy).equals(oldCopy)) {
                Files.delete(copy);
            }
        } catch (IOException unlinked) {
            throw new UncheckedIOException(unlinked);
        }
    }

    private void fetchTheBranch(ProjectEntry project, BranchName branch, Path oldCopy) {
        try {
            git.fetchBranchInto(project.projectClone(), oldCopy, branch);
        } catch (ProgramFailure diverged) {
            throw new Refusal(Rule.ADOPTABLE, project.project().name() + "'s clone holds " + branch
                    + " at a commit the old copy does not build on, so I cannot take it"
                    + " over: " + diverged.getMessage());
        }
    }

    private static void createParentOf(Path copy) {
        try {
            Files.createDirectories(copy.getParent());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static void moveTheWorkingTree(Path oldCopy, Path copy) {
        try (Stream<Path> entries = Files.list(oldCopy)) {
            for (Path entry : entries.filter(entry -> !entry.getFileName().toString().equals(GIT_LINK)).toList()) {
                Files.move(entry, copy.resolve(entry.getFileName().toString()));
            }
            Files.delete(oldCopy.resolve(GIT_LINK));
            Files.delete(oldCopy);
        } catch (IOException unmoved) {
            throw new UncheckedIOException(unmoved);
        }
    }

    private record RegisteredCopy(Path copy, boolean moved) {
    }
}
