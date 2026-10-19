package io.github.ythibos.edi.application.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.runtime.ClaudeSessions;
import io.github.ythibos.edi.adapter.store.AdoptionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.dispatch.IsolatedCopies;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdopted;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Adoption;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class AdoptForkTask {

    private static final int SHOWN_PATHS = 3;

    private final EdiHome home;
    private final ForkMigrationPlanner planner;
    private final BacklogImport backlog;
    private final ProjectRegistry registry;
    private final IsolatedCopies copies;
    private final AdoptionStore adoptions;
    private final ClaudeSessions sessions;
    private final EventLog events;
    private final Git git;
    private final ProcessTable processes;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AdoptForkTask(EdiHome home, ForkMigrationPlanner planner, BacklogImport backlog, ProjectRegistry registry, IsolatedCopies copies,
            AdoptionStore adoptions, ClaudeSessions sessions, EventLog events, Git git, ProcessTable processes, Clock clock,
            TransactionTemplate transaction) {
        this.home = home;
        this.planner = planner;
        this.backlog = backlog;
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
        MigratedEntry entry = keyedByItsBranch(planner.entryOf(oldHome, id), branch);
        ProjectEntry project = registry.entryOf(onlyProjectOf(entry.task()));
        requireAQuietCleanCopy(oldCopy, branch);
        Optional<Path> transcript = transcriptOf(session);
        Path copy = copyOn(project, entry.task(), branch, oldCopy);
        transcript.ifPresent(found -> sessions.carryInto(found, copy));
        transaction.executeWithoutResult(_ -> recordedAdoption(entry, oldHome, branch, oldCopy, session, actor));
        return new AdoptedTask(id, branch, copy, git.headSummary(copy), oldCopy, session);
    }

    private static MigratedEntry keyedByItsBranch(MigratedEntry entry, BranchName branch) {
        Task task = entry.task();
        if (task.isClosed()) {
            throw new Refusal(task.id() + " is " + task.state().word() + " in the old home, and only open work is taken over.");
        }
        if (task.intake().workKey() == null) {
            return new MigratedEntry(task.keyedOn(branch.workKey()), entry.decision());
        }
        if (!task.intake().workKey().equals(branch.workKey())) {
            throw new Refusal(task.id() + " books its work on " + task.intake().workKey() + ", but " + branch + " carries " + branch.workKey() + ".");
        }
        return entry;
    }

    private static ProjectName onlyProjectOf(Task task) {
        List<ProjectName> projects = task.intake().projects();
        if (projects.size() != 1) {
            throw new Refusal(task.id() + " works on " + projects.size() + " projects, and EDI takes over the isolated copy of exactly one.");
        }
        return projects.getFirst();
    }

    private void requireAQuietCleanCopy(Path oldCopy, BranchName branch) {
        requireTheBranchIn(oldCopy, branch);
        requireNothingWorkingIn(oldCopy);
        requireNothingUncommittedIn(oldCopy, branch);
    }

    private Optional<Path> transcriptOf(UUID session) {
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(sessions.transcriptOf(session).orElseThrow(() -> new Refusal("No Claude Code transcript of session " + session
                + " exists on this machine, so EDI could not resume it; adopt without --session to start the next worker afresh.")));
    }

    private Path copyOn(ProjectEntry project, Task task, BranchName branch, Path oldCopy) {
        Path copy = home.worktreeOf(project.project().name(), task.id());
        BaseBranch base = task.intake().base() == null ? project.requireBase() : task.intake().base();
        if (Files.isDirectory(copy)) {
            requireEveryCommitOf(oldCopy, project, branch);
        } else {
            copies.requireAClone(project);
            fetchTheBranch(project, branch, oldCopy);
        }
        return copies.prepare(project, task.id(), branch, base);
    }

    private void recordedAdoption(MigratedEntry entry, Path oldHome, BranchName branch, Path oldCopy, UUID session, Actor actor) {
        backlog.importInto(List.of(entry), oldHome, actor);
        TaskId task = entry.task().id();
        if (adoptions.of(task).filter(earlier -> earlier.continues(branch, session)).isPresent()) {
            return;
        }
        Instant now = clock.now();
        adoptions.save(new Adoption(task, branch, session, now));
        events.record(new TaskAdopted(task, branch, oldCopy.toString(), session), actor, now);
    }

    private void requireTheBranchIn(Path oldCopy, BranchName branch) {
        if (!git.isRepository(oldCopy)) {
            throw new Refusal(oldCopy + " is not a git copy, so it holds no branch to take over.");
        }
        String checkedOut = git.currentBranch(oldCopy);
        if (!checkedOut.equals(branch.toString())) {
            throw new Refusal(oldCopy + " has " + checkedOut + " checked out, not " + branch + "; name the copy that holds the task's branch.");
        }
    }

    private void requireNothingWorkingIn(Path oldCopy) {
        List<Long> holders = processes.workingIn(oldCopy);
        if (!holders.isEmpty()) {
            throw new Refusal("Processes " + holders + " still work in " + oldCopy + "; stop the old harness's worker there first.");
        }
    }

    private void requireNothingUncommittedIn(Path oldCopy, BranchName branch) {
        List<String> uncommitted = git.uncommittedPaths(oldCopy);
        if (!uncommitted.isEmpty()) {
            throw new Refusal(oldCopy + " holds " + Counted.things(uncommitted.size(), "uncommitted change") + ", such as "
                    + String.join(", ", uncommitted.subList(0, Math.min(SHOWN_PATHS, uncommitted.size()))) + "; commit that work on " + branch
                    + " first, so nothing is left behind.");
        }
    }

    private void requireEveryCommitOf(Path oldCopy, ProjectEntry project, BranchName branch) {
        String oldHead = git.head(oldCopy);
        if (git.commitAt(project.projectClone(), oldHead).isEmpty() || !git.isAncestor(project.projectClone(), oldHead, branch.toString())) {
            throw new Refusal("EDI's copy of " + branch + " lacks commits the old copy holds; bring them over before taking the task over.");
        }
    }

    private void fetchTheBranch(ProjectEntry project, BranchName branch, Path oldCopy) {
        try {
            git.fetchBranchInto(project.projectClone(), oldCopy, branch);
        } catch (ProgramFailure diverged) {
            throw new Refusal(project.project().name() + "'s clone holds " + branch + " at a commit the old copy does not build on, so EDI cannot take it"
                    + " over: " + diverged.getMessage());
        }
    }
}
