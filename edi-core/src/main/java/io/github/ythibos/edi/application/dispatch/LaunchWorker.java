package io.github.ythibos.edi.application.dispatch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.DispatchRulesFile;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.WorkerSettings;
import io.github.ythibos.edi.adapter.machine.MachineBoot;
import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.AdoptionStore;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.OutageStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.application.brief.WorkerBriefs;
import io.github.ythibos.edi.application.brief.WrittenBriefs;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.worker.WorkerTokens;
import io.github.ythibos.edi.application.worker.WorkerTokens.IssuedToken;
import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.task.Adoption;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Service
public class LaunchWorker {

    private final EdiHome home;
    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final WorkerStore workers;
    private final AdoptionStore adoptions;
    private final OutageStore outages;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final MachineProfile machine;
    private final DispatchRulesFile rulesFile;
    private final IsolatedCopies copies;
    private final WorkerPlaces places;
    private final WorkerBriefs briefs;
    private final WrittenBriefs writtenBriefs;
    private final GatherBriefFacts briefFacts;
    private final ResumeNotes resumeNotes;
    private final WorkerTokens tokens;
    private final WorkerRuntime runtime;
    private final ProcessTable processes;
    private final MachineBoot boot;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public LaunchWorker(EdiHome home, TaskStore tasks, DispatchStore dispatches, WorkerStore workers, AdoptionStore adoptions, OutageStore outages,
            EventLog events, ProjectRegistry registry, MachineProfile machine, DispatchRulesFile rulesFile, IsolatedCopies copies,
            WorkerPlaces places, WorkerBriefs briefs, WrittenBriefs writtenBriefs, GatherBriefFacts briefFacts, ResumeNotes resumeNotes,
            WorkerTokens tokens, WorkerRuntime runtime, ProcessTable processes, MachineBoot boot, Clock clock, TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.workers = workers;
        this.adoptions = adoptions;
        this.outages = outages;
        this.events = events;
        this.registry = registry;
        this.machine = machine;
        this.rulesFile = rulesFile;
        this.copies = copies;
        this.places = places;
        this.briefs = briefs;
        this.writtenBriefs = writtenBriefs;
        this.briefFacts = briefFacts;
        this.resumeNotes = resumeNotes;
        this.tokens = tokens;
        this.runtime = runtime;
        this.processes = processes;
        this.boot = boot;
        this.clock = clock;
        this.transaction = transaction;
    }

    public synchronized Launched launch(Dispatch dispatch, String because, Actor actor) {
        return launched(dispatch, LaunchKind.DISPATCH, null, because, actor);
    }

    public synchronized Launched relaunch(TaskId task, LaunchKind kind, CaptainOverride captainsOverride, OutageEntry answered, String because,
            Actor actor) {
        requireAnEmptyCopy(task);
        Dispatch dispatch = transaction.execute(_ -> dispatchToRelaunch(task, captainsOverride));
        return launched(dispatch, kind, answered, because, actor);
    }

    public String briefFor(Dispatch dispatch) {
        return briefs.briefFor(transaction.execute(_ -> incarnationFor(dispatch, LaunchKind.DISPATCH, null)).brief());
    }

    private void requireAnEmptyCopy(TaskId task) {
        Path worktree = home.worktreeOf(transaction.execute(_ -> tasks.get(task)).intake().projects().getFirst(), task);
        List<Long> holders = processes.workingIn(worktree);
        if (!holders.isEmpty()) {
            throw new Refusal("Processes " + holders + " still work in " + worktree + "; EDI relaunches a worker only in a copy nothing else holds.");
        }
    }

    private Launched launched(Dispatch dispatch, LaunchKind kind, OutageEntry answered, String because, Actor actor) {
        Incarnation incarnation = transaction.execute(_ -> incarnationFor(dispatch, kind, answered));
        Path worktree = copies.prepare(incarnation.project(), dispatch.task(), dispatch.branch(), incarnation.brief().base());
        copies.requireIsolated(worktree, incarnation.project());
        Path taskDirectory = createdDirectory(home.taskDirectory(dispatch.task()));
        Path stateDirectory = createdDirectory(home.workerState(incarnation.worker()));
        IssuedToken token = tokens.issueInto(stateDirectory);
        String firstPrompt = kind == LaunchKind.RESUME
                ? resumeNotes.noteFor(incarnation.earlier(), worktree, dispatch.branch(), because)
                : briefPrompt(writtenBriefs.written(incarnation.brief()));
        WorkerWindow window = runtime.launch(launchOf(incarnation, worktree, taskDirectory, stateDirectory, token, firstPrompt));
        return transaction.execute(_ -> recorded(incarnation, window, token, worktree, because, actor));
    }

    private Dispatch dispatchToRelaunch(TaskId task, CaptainOverride captainsOverride) {
        Dispatch dispatch = dispatches.of(task).orElseThrow(() -> new Refusal(task + " was never dispatched, so there is nothing to relaunch."));
        if (captainsOverride == null) {
            return dispatch;
        }
        Dispatch overridden = dispatch.overriddenBy(captainsOverride);
        dispatches.save(overridden);
        return overridden;
    }

    private Incarnation incarnationFor(Dispatch dispatch, LaunchKind kind, OutageEntry answered) {
        Task task = withoutTheOutageHold(tasks.get(dispatch.task()), answered).dispatched();
        ProjectEntry project = registry.entryOf(task.intake().projects().getFirst());
        WorkerSettings settings = machine.workers();
        Route route = rulesFile.validatedAgainst(settings.models()).routeFor(task.intake().projects(), dispatch.category(), dispatch.captainsOverride());
        places.requireNoLiveWorker(task.id());
        Worker earlier = workers.latestOf(task.id()).orElse(null);
        WorkerId worker = earlier == null ? WorkerId.first(task.id()) : earlier.id().next();
        Optional<UUID> adoptedSession = adoptedSessionToResume(task, earlier);
        return new Incarnation(task, project, dispatch, kind, adoptedSession.isPresent() ? LaunchKind.RESUME : kind, worker,
                adoptedSession.orElseGet(() -> sessionFor(kind, task, earlier)), earlier, route, settings,
                briefFacts.factsFor(task, project, dispatch, worker), answered);
    }

    private static Task withoutTheOutageHold(Task task, OutageEntry answered) {
        return answered != null && task.isHeld() && answered.holds(task.hold()) ? task.released() : task;
    }

    private Optional<UUID> adoptedSessionToResume(Task task, Worker earlier) {
        return earlier == null ? adoptions.of(task.id()).filter(Adoption::resumesASession).map(Adoption::session) : Optional.empty();
    }

    private static UUID sessionFor(LaunchKind kind, Task task, Worker earlier) {
        if (kind != LaunchKind.RESUME) {
            return UUID.randomUUID();
        }
        if (earlier == null) {
            throw new Refusal(task.id() + " never had a worker, so there is no session to resume.");
        }
        return earlier.session();
    }

    private static String briefPrompt(Path brief) {
        return OperationalText.marked("brief waiting: read " + brief + " and follow it");
    }

    private Launch launchOf(Incarnation incarnation, Path worktree, Path taskDirectory, Path stateDirectory, IssuedToken token, String firstPrompt) {
        Map<String, String> environment = Map.of(
                "EDI_HOME", home.root().toString(),
                "EDI_ROLE", "worker",
                "EDI_TASK_ID", incarnation.task().id().value(),
                "EDI_WORKER", incarnation.worker().toString(),
                "EDI_TOKEN_FILE", token.file().toString());
        return new Launch(incarnation.worker(), incarnation.launchedAs(), incarnation.session(), incarnation.route(), worktree,
                incarnation.project().projectClone(), taskDirectory, stateDirectory, environment,
                incarnation.settings().disallowedTools(), incarnation.settings().permissionMode(), firstPrompt);
    }

    private Launched recorded(Incarnation incarnation, WorkerWindow window, IssuedToken token, Path worktree, String because, Actor actor) {
        Instant now = clock.now();
        Worker worker = Worker.launched(incarnation.worker(), incarnation.session(), incarnation.dispatch().category(),
                incarnation.route(), window, new WorkerLaunch(incarnation.launchedAs(), now, boot.current()));
        retireTheEarlierWorker(incarnation, now, actor);
        workers.insert(worker, token.digest());
        settleTheOutageEntry(incarnation, now, actor);
        tasks.update(tasks.get(incarnation.task().id()).dispatched());
        dispatches.save(incarnation.dispatch().undeferred());
        events.record(launchEventOf(incarnation, worker, because), actor, now);
        return new Launched(worker, incarnation.dispatch().branch(), worktree);
    }

    private void retireTheEarlierWorker(Incarnation incarnation, Instant now, Actor actor) {
        Worker earlier = incarnation.earlier();
        if (earlier != null && !earlier.isRetired()) {
            workers.update(earlier.retired(now));
            events.record(new WorkerRetired(earlier.id().task(), earlier.id(), retirementOf(incarnation.kind())), actor, now);
        }
    }

    private void settleTheOutageEntry(Incarnation incarnation, Instant now, Actor actor) {
        OutageEntry answered = incarnation.answered();
        if (answered == null) {
            return;
        }
        Task task = tasks.get(answered.task());
        Task released = withoutTheOutageHold(task, answered);
        if (task.isHeld() && !released.isHeld()) {
            tasks.update(released);
            events.record(new TaskReleased(released.id()), actor, now);
        }
        outages.close(answered);
    }

    private static String retirementOf(LaunchKind kind) {
        return switch (kind) {
            case DISPATCH -> "the task was dispatched again after its window closed";
            case RESUME -> "a new incarnation resumed its session";
            case FRESH -> "the task was started afresh in the same copy";
        };
    }

    private static DomainEvent launchEventOf(Incarnation incarnation, Worker worker, String because) {
        TaskId task = incarnation.task().id();
        return incarnation.kind() == LaunchKind.DISPATCH
                ? new TaskDispatched(task, worker.id(), worker.category(), worker.route(), incarnation.dispatch().branch(), because)
                : new WorkerRelaunched(task, worker.id(), incarnation.kind(), worker.route(), because);
    }

    private static Path createdDirectory(Path directory) {
        try {
            return Files.createDirectories(directory);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private record Incarnation(
            Task task,
            ProjectEntry project,
            Dispatch dispatch,
            LaunchKind kind,
            LaunchKind launchedAs,
            WorkerId worker,
            UUID session,
            Worker earlier,
            Route route,
            WorkerSettings settings,
            BriefFacts brief,
            OutageEntry answered) {
    }
}
