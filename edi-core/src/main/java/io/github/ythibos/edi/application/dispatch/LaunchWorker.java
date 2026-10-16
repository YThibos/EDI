package io.github.ythibos.edi.application.dispatch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.DispatchRulesFile;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.WorkerSettings;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.brief.BriefFacts;
import io.github.ythibos.edi.application.brief.WorkerBriefs;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.worker.WorkerTokens;
import io.github.ythibos.edi.application.worker.WorkerTokens.IssuedToken;
import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Service
public class LaunchWorker {

    private static final String BRIEF_FILE = "brief.md";

    private final EdiHome home;
    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final WorkerStore workers;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final MachineProfile machine;
    private final DispatchRulesFile rulesFile;
    private final IsolatedCopies copies;
    private final WorkerPlaces places;
    private final WorkerBriefs briefs;
    private final WorkerTokens tokens;
    private final WorkerRuntime runtime;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public LaunchWorker(EdiHome home, TaskStore tasks, DispatchStore dispatches, WorkerStore workers, EventLog events,
            ProjectRegistry registry, MachineProfile machine, DispatchRulesFile rulesFile, IsolatedCopies copies, WorkerPlaces places,
            WorkerBriefs briefs, WorkerTokens tokens, WorkerRuntime runtime, Clock clock, TransactionTemplate transaction) {
        this.home = home;
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.workers = workers;
        this.events = events;
        this.registry = registry;
        this.machine = machine;
        this.rulesFile = rulesFile;
        this.copies = copies;
        this.places = places;
        this.briefs = briefs;
        this.tokens = tokens;
        this.runtime = runtime;
        this.clock = clock;
        this.transaction = transaction;
    }

    public synchronized Launched launch(Dispatch dispatch, String because, Actor actor) {
        Incarnation incarnation = transaction.execute(_ -> incarnationFor(dispatch));
        Path worktree = copies.prepare(incarnation.project(), dispatch.task(), dispatch.branch(), incarnation.base());
        copies.requireIsolated(worktree, incarnation.project());
        Path taskDirectory = createdDirectory(home.taskDirectory(dispatch.task()));
        Path stateDirectory = createdDirectory(home.workerState(incarnation.worker()));
        IssuedToken token = tokens.issueInto(stateDirectory);
        Path brief = written(taskDirectory.resolve(BRIEF_FILE), briefs.briefFor(briefFacts(incarnation, worktree, taskDirectory)));
        WorkerWindow window = runtime.launch(launchOf(incarnation, worktree, taskDirectory, stateDirectory, token, brief));
        return transaction.execute(_ -> recorded(incarnation, window, token, worktree, because, actor));
    }

    public String briefFor(Dispatch dispatch) {
        Incarnation incarnation = transaction.execute(_ -> incarnationFor(dispatch));
        Path worktree = home.worktreeOf(incarnation.project().project().name(), dispatch.task());
        return briefs.briefFor(briefFacts(incarnation, worktree, home.taskDirectory(dispatch.task())));
    }

    private Incarnation incarnationFor(Dispatch dispatch) {
        Task task = tasks.get(dispatch.task()).dispatched();
        ProjectEntry project = registry.entryOf(task.intake().projects().getFirst());
        WorkerSettings settings = machine.workers();
        Route route = rulesFile.validatedAgainst(settings.models()).routeFor(task.intake().projects(), dispatch.category(), dispatch.captainsOverride());
        places.requireNoLiveWorker(task.id());
        Worker earlier = workers.latestOf(task.id()).orElse(null);
        WorkerId worker = earlier == null ? WorkerId.first(task.id()) : earlier.id().next();
        BaseBranch base = task.intake().base() == null ? project.requireBase() : task.intake().base();
        return new Incarnation(task, project, dispatch, worker, UUID.randomUUID(), earlier, route, base, settings, craftRulesFor(project));
    }

    private String craftRulesFor(ProjectEntry project) {
        if (!project.craftRules()) {
            return null;
        }
        try {
            return Files.readString(home.craftRules(), StandardCharsets.UTF_8);
        } catch (NoSuchFileException absent) {
            throw new Refusal(project.project().name() + " follows the craft rules, but " + home.craftRules() + " is missing; write the rules block there first.");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private BriefFacts briefFacts(Incarnation incarnation, Path worktree, Path taskDirectory) {
        return new BriefFacts(incarnation.task(), incarnation.worker(), incarnation.project(), incarnation.dispatch(), incarnation.base(),
                worktree, taskDirectory, runtime.socket(), incarnation.craftRules());
    }

    private Launch launchOf(Incarnation incarnation, Path worktree, Path taskDirectory, Path stateDirectory, IssuedToken token, Path brief) {
        Map<String, String> environment = Map.of(
                "EDI_HOME", home.root().toString(),
                "EDI_ROLE", "worker",
                "EDI_TASK_ID", incarnation.task().id().value(),
                "EDI_WORKER", incarnation.worker().toString(),
                "EDI_TOKEN_FILE", token.file().toString());
        return new Launch(incarnation.worker(), incarnation.session(), incarnation.route(), worktree, incarnation.project().projectClone(),
                taskDirectory, stateDirectory, environment,
                incarnation.settings().disallowedTools(), incarnation.settings().permissionMode(),
                OperationalText.marked("brief waiting: read " + brief + " and follow it"));
    }

    private Launched recorded(Incarnation incarnation, WorkerWindow window, IssuedToken token, Path worktree, String because, Actor actor) {
        Instant now = clock.now();
        Worker worker = Worker.launched(incarnation.worker(), incarnation.session(), incarnation.dispatch().category(),
                incarnation.route(), window, now);
        retireTheEarlierWorker(incarnation, now, actor);
        workers.insert(worker, token.digest());
        tasks.update(tasks.get(incarnation.task().id()).dispatched());
        dispatches.save(incarnation.dispatch().undeferred());
        events.record(new TaskDispatched(incarnation.task().id(), worker.id(), worker.category(), worker.route(), incarnation.dispatch().branch(),
                because), actor, now);
        return new Launched(worker, incarnation.dispatch().branch(), worktree);
    }

    private void retireTheEarlierWorker(Incarnation incarnation, Instant now, Actor actor) {
        Worker earlier = incarnation.earlier();
        if (earlier != null && !earlier.isRetired()) {
            workers.update(earlier.retired(now));
            events.record(new WorkerRetired(earlier.id().task(), earlier.id(), "the task was dispatched again after its window closed"), actor, now);
        }
    }

    private static Path createdDirectory(Path directory) {
        try {
            return Files.createDirectories(directory);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static Path written(Path file, String content) {
        try {
            return Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private record Incarnation(
            Task task,
            ProjectEntry project,
            Dispatch dispatch,
            WorkerId worker,
            UUID session,
            Worker earlier,
            Route route,
            BaseBranch base,
            WorkerSettings settings,
            String craftRules) {
    }
}
