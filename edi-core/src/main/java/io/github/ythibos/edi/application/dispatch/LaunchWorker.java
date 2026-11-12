package io.github.ythibos.edi.application.dispatch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import io.github.ythibos.edi.adapter.health.CoreMeters;
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
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.UnresumedSession;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.application.task.TaskHolds;
import io.github.ythibos.edi.application.task.TaskStages;
import io.github.ythibos.edi.application.worker.WorkerTokens;
import io.github.ythibos.edi.application.worker.WorkerTokens.IssuedToken;
import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.DispatchRules;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.Adoption;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.StageChange;
import io.github.ythibos.edi.domain.task.StageTrigger;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

@Service
public class LaunchWorker {

    private static final String HANDOVER_FILE = "handover.md";

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
    private final Handovers handovers;
    private final WorkerTokens tokens;
    private final WorkerRuntime runtime;
    private final Pipeline pipeline;
    private final ProcessTable processes;
    private final MachineBoot boot;
    private final TaskHolds holds;
    private final TaskStages stages;
    private final CoreMeters meters;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public LaunchWorker(EdiHome home, TaskStore tasks, DispatchStore dispatches, WorkerStore workers, AdoptionStore adoptions, OutageStore outages,
            EventLog events, ProjectRegistry registry, MachineProfile machine, DispatchRulesFile rulesFile, IsolatedCopies copies,
            WorkerPlaces places, WorkerBriefs briefs, WrittenBriefs writtenBriefs, GatherBriefFacts briefFacts, ResumeNotes resumeNotes,
            Handovers handovers, WorkerTokens tokens, WorkerRuntime runtime, Pipeline pipeline, ProcessTable processes, MachineBoot boot, TaskHolds holds,
            TaskStages stages, CoreMeters meters, Clock clock, TransactionTemplate transaction) {
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
        this.handovers = handovers;
        this.tokens = tokens;
        this.runtime = runtime;
        this.pipeline = pipeline;
        this.processes = processes;
        this.boot = boot;
        this.holds = holds;
        this.stages = stages;
        this.meters = meters;
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
        BriefFacts facts = dispatchedBriefFactsOf(dispatch);
        briefs.requireWithinItsLength(facts);
        return briefs.briefFor(facts);
    }

    public void requireABriefWithinItsLength(Dispatch dispatch) {
        briefs.requireWithinItsLength(dispatchedBriefFactsOf(dispatch));
    }

    public void requireAReadyLoginFor(ProjectName project) {
        runtime.requireReady(loginOf(registry.entryOf(project)));
    }

    private void requireAnEmptyCopy(TaskId task) {
        Path worktree = home.worktreeOf(transaction.execute(_ -> tasks.get(task)).intake().projects().getFirst(), task);
        List<Long> holders = processes.workingIn(worktree);
        if (!holders.isEmpty()) {
            throw new Refusal(Rule.RELAUNCH_STOPPED_FIRST, "Processes " + holders + " still work in " + worktree
                    + "; a worker is relaunched only in a copy nothing else holds.");
        }
    }

    private BriefFacts dispatchedBriefFactsOf(Dispatch dispatch) {
        return transaction.execute(_ -> incarnationFor(dispatch, LaunchKind.DISPATCH, null)).brief();
    }

    private Login loginOf(ProjectEntry project) {
        return machine.claudeLogins().named(project.login());
    }

    private Launched launched(Dispatch dispatch, LaunchKind kind, OutageEntry answered, String because, Actor actor) {
        return meters.workerLaunch(() -> launchedNow(dispatch, kind, answered, because, actor));
    }

    private Launched launchedNow(Dispatch dispatch, LaunchKind kind, OutageEntry answered, String because, Actor actor) {
        Incarnation incarnation = transaction.execute(_ -> incarnationFor(dispatch, kind, answered));
        runtime.requireReady(incarnation.login());
        Optional<StageChange> start = transaction.execute(_ -> startOf(incarnation));
        Path worktree = copies.prepare(incarnation.project(), dispatch.task(), dispatch.branch(), incarnation.brief().base());
        copies.requireIsolated(worktree, incarnation.project());
        Path taskDirectory = createdDirectory(home.taskDirectory(dispatch.task()));
        Path stateDirectory = createdDirectory(home.workerState(incarnation.worker()));
        IssuedToken token = tokens.issueInto(stateDirectory);
        String reason = reasonFor(incarnation, because);
        String firstPrompt = firstPromptFor(incarnation, worktree, reason);
        prepareThePipelineFor(incarnation);
        WorkerWindow window = runtime.launch(launchOf(incarnation, worktree, taskDirectory, stateDirectory, token, firstPrompt));
        return transaction.execute(_ -> recorded(incarnation, start, window, token, worktree, reason, actor));
    }

    private Optional<StageChange> startOf(Incarnation incarnation) {
        Task task = incarnation.task();
        if (task.stage() != TaskStage.QUEUED) {
            return Optional.empty();
        }
        return Optional.of(task.transitions().move(task.stage(), dispatchOf(task, incarnation.project())));
    }

    private StageTrigger.Dispatch dispatchOf(Task task, ProjectEntry project) {
        List<TaskId> unmetWaits = tasks.blockersOf(task).stream().filter(blocker -> !blocker.isResolved()).map(Blocker::id).toList();
        return new StageTrigger.Dispatch(holds.of(task), unmetWaits, places.roomOn(project).hasRoom());
    }

    private Dispatch dispatchToRelaunch(TaskId task, CaptainOverride captainsOverride) {
        Dispatch dispatch = dispatches.of(task).orElseThrow(() -> new Refusal(Rule.TASK_DISPATCHED, task
                + " was never dispatched, so there is nothing to relaunch."));
        if (captainsOverride == null) {
            return dispatch;
        }
        Dispatch overridden = dispatch.overriddenBy(captainsOverride);
        dispatches.save(overridden);
        return overridden;
    }

    private Incarnation incarnationFor(Dispatch dispatch, LaunchKind kind, OutageEntry answered) {
        Task task = withoutTheOutageHold(tasks.get(dispatch.task()), answered);
        task.requireFreeToWork();
        ProjectEntry project = registry.entryOf(task.intake().projects().getFirst());
        WorkerSettings settings = machine.workers();
        DispatchRules rules = rulesFile.validatedAgainst(settings.models());
        Route route = rules.routeFor(task.intake().projects(), dispatch.category(), dispatch.captainsOverride());
        places.requireNoLiveWorker(task.id());
        Worker earlier = workers.latestOf(task.id()).orElse(null);
        WorkerId worker = earlier == null ? WorkerId.first(task.id()) : earlier.id().next();
        SessionStart start = sessionStartFor(kind, task, earlier, project, answered);
        return new Incarnation(task, project, start.login(), dispatch, start.kind(), start.launchedAs(), worker, start.session(), start.unresumed(), earlier,
                route, rules.pipelineAgent().route(), settings, briefFacts.factsFor(task, project, dispatch, worker), answered);
    }

    private static Task withoutTheOutageHold(Task task, OutageEntry answered) {
        return answered != null && task.isHeld() && answered.holds(task.hold()) ? task.released() : task;
    }

    private SessionStart sessionStartFor(LaunchKind kind, Task task, Worker earlier, ProjectEntry project, OutageEntry answered) {
        Optional<UUID> adoptedSession = adoptedSessionToResume(task, earlier);
        if (adoptedSession.isPresent()) {
            return resumed(kind, adoptedSession.get(), loginOfAnAdoptedSession(), project);
        }
        if (kind != LaunchKind.RESUME) {
            return new SessionStart(kind, kind, UUID.randomUUID(), loginOf(project), null);
        }
        return resumed(kind, sessionToResume(task, earlier), recordedLoginOf(earlier, answered), project);
    }

    private Optional<UUID> adoptedSessionToResume(Task task, Worker earlier) {
        return earlier == null ? adoptions.of(task.id()).filter(Adoption::resumesASession).map(Adoption::session) : Optional.empty();
    }

    private SessionStart resumed(LaunchKind kind, UUID session, Login login, ProjectEntry project) {
        if (runtime.hasATranscriptOf(session, login)) {
            return new SessionStart(kind, LaunchKind.RESUME, session, login, null);
        }
        return SessionStart.insteadOfResuming(UnresumedSession.withoutATranscript(session), kind, loginOf(project));
    }

    private Login loginOfAnAdoptedSession() {
        return machine.claudeLogins().named(LoginName.DEFAULT);
    }

    private static UUID sessionToResume(Task task, Worker earlier) {
        if (earlier == null) {
            throw new Refusal(Rule.TASK_DISPATCHED, task.id() + " never had a worker, so there is no session to resume.");
        }
        if (earlier.hasAFullContext()) {
            throw new Refusal(Rule.RELAUNCH_STOPPED_FIRST, earlier.id()
                    + "'s context is full, so a resumed session would stop on it again; start it fresh instead.");
        }
        return earlier.session();
    }

    private Login recordedLoginOf(Worker earlier, OutageEntry answered) {
        LoginName recorded = earlier.launch().login();
        if (!machine.claudeLogins().names(recorded)) {
            throw new Refusal(Rule.CLAUDE_LOGIN, earlier.id() + "'s session ran on the Claude login " + recorded + ", which the machine profile no longer names, and a"
                    + " session resumes only on its own login; restore " + recorded + " under claude.logins to resume it, or start it fresh with "
                    + freshStartOf(earlier, answered) + ".");
        }
        return machine.claudeLogins().named(recorded);
    }

    private static String freshStartOf(Worker earlier, OutageEntry answered) {
        return answered == null ? "edi worker relaunch " + earlier.id().task() + " --fresh" : "edi recover " + answered.number() + " fresh";
    }

    private static String reasonFor(Incarnation incarnation, String because) {
        UnresumedSession unresumed = incarnation.unresumed();
        return unresumed == null ? because : because + " and " + unresumed.sentence();
    }

    private String firstPromptFor(Incarnation incarnation, Path worktree, String because) {
        return switch (incarnation.kind()) {
            case DISPATCH -> briefPrompt(writtenBriefs.written(incarnation.brief()));
            case RESUME -> resumeNotes.noteFor(incarnation.earlier(), worktree, incarnation.dispatch().branch(), because);
            case FRESH -> handoverPrompt(writtenHandover(incarnation.brief(), incarnation.earlier(), because), because);
        };
    }

    private static String briefPrompt(Path brief) {
        return OperationalText.marked("brief waiting: read " + brief + " and follow it");
    }

    private Path writtenHandover(BriefFacts facts, Worker earlier, String because) {
        Path brief = writtenBriefs.written(facts);
        return written(facts.taskDirectory().resolve(HANDOVER_FILE), handovers.handoverFor(facts, earlier, brief, because));
    }

    private static String handoverPrompt(Path handover, String because) {
        return OperationalText.marked("fresh start after " + because + ": read the handover " + handover + " and carry on from where it leaves off");
    }

    private void prepareThePipelineFor(Incarnation incarnation) {
        pipeline.runAgentOn(incarnation.pipelineRoute());
        if (incarnation.brief().deliversThroughThePipeline()) {
            copies.forgeRemoteOf(incarnation.project()).ifPresent(pipeline::keyCommitsPushedTo);
        }
    }

    private Launch launchOf(Incarnation incarnation, Path worktree, Path taskDirectory, Path stateDirectory, IssuedToken token, String firstPrompt) {
        Map<String, String> environment = Map.of(
                "EDI_HOME", home.root().toString(),
                "EDI_ROLE", "worker",
                "EDI_TASK_ID", incarnation.task().id().value(),
                "EDI_WORKER", incarnation.worker().toString(),
                "EDI_TOKEN_FILE", token.file().toString());
        return new Launch(incarnation.worker(), incarnation.launchedAs(), incarnation.session(), incarnation.route(), incarnation.login(), worktree,
                incarnation.project().projectClone(), taskDirectory, stateDirectory, environment,
                incarnation.settings().disallowedTools(), incarnation.settings().permissionMode(), firstPrompt);
    }

    private Launched recorded(Incarnation incarnation, Optional<StageChange> start, WorkerWindow window, IssuedToken token, Path worktree,
            String because, Actor actor) {
        Instant now = clock.now();
        Worker worker = Worker.launched(incarnation.worker(), incarnation.session(), incarnation.dispatch().category(),
                incarnation.route(), window, new WorkerLaunch(incarnation.launchedAs(), now, boot.current(), incarnation.login().name()));
        retireTheEarlierWorker(incarnation, now, actor);
        workers.insert(worker, token.digest());
        settleTheOutageEntry(incarnation, now, actor);
        dispatches.save(incarnation.dispatch().undeferred());
        events.record(launchEventOf(incarnation, worker, because), actor, now);
        start.ifPresent(change -> stages.changed(tasks.get(change.task()), change, actor, now));
        return new Launched(worker, incarnation.dispatch().branch(), worktree, incarnation.unresumed());
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

    private static Path written(Path file, String content) {
        try {
            return Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
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
            Login login,
            Dispatch dispatch,
            LaunchKind kind,
            LaunchKind launchedAs,
            WorkerId worker,
            UUID session,
            UnresumedSession unresumed,
            Worker earlier,
            Route route,
            Route pipelineRoute,
            WorkerSettings settings,
            BriefFacts brief,
            OutageEntry answered) {
    }

    private record SessionStart(LaunchKind kind, LaunchKind launchedAs, UUID session, Login login, UnresumedSession unresumed) {

        static SessionStart insteadOfResuming(UnresumedSession unresumed, LaunchKind kind, Login projectsLogin) {
            LaunchKind instead = freshInsteadOf(kind);
            return new SessionStart(instead, instead, UUID.randomUUID(), projectsLogin, unresumed);
        }

        private static LaunchKind freshInsteadOf(LaunchKind kind) {
            return kind == LaunchKind.RESUME ? LaunchKind.FRESH : kind;
        }
    }
}
