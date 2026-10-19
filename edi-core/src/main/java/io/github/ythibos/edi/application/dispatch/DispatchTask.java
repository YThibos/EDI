package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.DispatchRulesFile;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.home.WorkerSettings;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.delivery.CloneRefreshes;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.DispatchCategory;
import io.github.ythibos.edi.domain.dispatch.DispatchRules;
import io.github.ythibos.edi.domain.dispatch.ResearchFirst;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DispatchDeferred;
import io.github.ythibos.edi.domain.mechanical.ExpectedChange;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

@Service
public class DispatchTask {

    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final EventLog events;
    private final ProjectRegistry registry;
    private final MachineProfile machine;
    private final DispatchRulesFile rulesFile;
    private final WorkerPlaces places;
    private final LaunchWorker launcher;
    private final CloneRefreshes refreshes;
    private final ResearchBeforeImplementation research;
    private final AwayLimit awayLimit;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DispatchTask(TaskStore tasks, DispatchStore dispatches, EventLog events, ProjectRegistry registry, MachineProfile machine,
            DispatchRulesFile rulesFile, WorkerPlaces places, LaunchWorker launcher, CloneRefreshes refreshes, ResearchBeforeImplementation research,
            AwayLimit awayLimit, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.events = events;
        this.registry = registry;
        this.machine = machine;
        this.rulesFile = rulesFile;
        this.places = places;
        this.launcher = launcher;
        this.refreshes = refreshes;
        this.research = research;
        this.awayLimit = awayLimit;
        this.clock = clock;
        this.transaction = transaction;
    }

    public DispatchOutcome dispatch(TaskReference reference, DispatchRequest request, Actor actor) {
        return carriedOut(transaction.execute(_ -> dispatching(reference, request)), actor);
    }

    public String previewBrief(TaskReference reference, DispatchRequest request) {
        return launcher.briefFor(transaction.execute(_ -> dispatching(reference, request)).dispatch());
    }

    private Dispatching dispatching(TaskReference reference, DispatchRequest request) {
        Task task = tasks.get(reference).dispatched();
        requireUnblocked(task);
        ProjectEntry project = registry.entryOf(onlyProjectOf(task));
        WorkerSettings settings = machine.workers();
        DispatchRules rules = rulesFile.validatedAgainst(settings.models());
        Optional<Dispatch> earlier = dispatches.of(task.id());
        DispatchCategory category = rules.category(categoryOf(request, earlier));
        requireACauseOnlyForABugfix(request, category);
        CaptainOverride captainsOverride = captainsOverrideOf(request, earlier, settings);
        Dispatch dispatch = earlier
                .map(redone -> redispatch(redone, project, category, request, captainsOverride))
                .orElseGet(() -> firstDispatch(task, project, category, request, captainsOverride));
        places.requireNoLiveWorker(task.id());
        return new Dispatching(dispatch, project.project().name(), places.occupiedOn(project.project().name()), project.workerCapacity(),
                researchFirstFor(task, earlier.isPresent(), rules, category, request));
    }

    private void requireUnblocked(Task task) {
        List<Blocker> unfinished = tasks.blockersOf(task).stream().filter(blocker -> !blocker.isResolved()).toList();
        if (!unfinished.isEmpty()) {
            throw new Refusal(task.id() + " waits for " + unfinished.stream().map(blocker -> blocker.id().value()).toList()
                    + "; it is dispatched once they are done.");
        }
    }

    private static ProjectName onlyProjectOf(Task task) {
        List<ProjectName> projects = task.intake().projects();
        if (projects.size() != 1) {
            throw new Refusal(task.id() + " works on " + projects.size() + " projects, and a worker works in the isolated copy of exactly one.");
        }
        return projects.getFirst();
    }

    private static CategoryId categoryOf(DispatchRequest request, Optional<Dispatch> earlier) {
        return Optional.ofNullable(request.category())
                .or(() -> earlier.map(Dispatch::category))
                .orElseThrow(() -> new Refusal("A first dispatch names its category; the primary picks one from the dispatch rules."));
    }

    private static void requireACauseOnlyForABugfix(DispatchRequest request, DispatchCategory category) {
        if (request.causeUnknown() && !category.id().equals(CategoryId.BUGFIX)) {
            throw new Refusal("Only a " + CategoryId.BUGFIX + " dispatch says its cause is unknown; " + category.id() + " work is planned or not.");
        }
    }

    private static CaptainOverride captainsOverrideOf(DispatchRequest request, Optional<Dispatch> earlier, WorkerSettings settings) {
        CaptainOverride captainsOverride = Optional.ofNullable(request.captainsOverride())
                .or(() -> earlier.map(Dispatch::captainsOverride))
                .orElse(null);
        if (captainsOverride != null) {
            settings.models().requireAllows(captainsOverride.route(), "The captain's override");
        }
        return captainsOverride;
    }

    private static ResearchFirst researchFirstFor(Task task, boolean dispatchedBefore, DispatchRules rules, DispatchCategory category,
            DispatchRequest request) {
        if (dispatchedBefore || task.intake().kind() != TaskKind.SHIP) {
            return ResearchFirst.NOT_APPLICABLE;
        }
        return rules.researchFirstFor(task.intake().projects(), category.id(), request.causeUnknown());
    }

    private Dispatch redispatch(Dispatch earlier, ProjectEntry project, DispatchCategory category, DispatchRequest request,
            CaptainOverride captainsOverride) {
        String spec = request.spec().isBlank() ? earlier.spec() : request.spec();
        List<String> files = request.expectedFiles().isEmpty() && earlier.expectedChange() != null
                ? earlier.expectedChange().files()
                : request.expectedFiles();
        return new Dispatch(earlier.task(), category.id(), earlier.branch(), spec, expectedChangeOf(project, category, spec, files),
                captainsOverride, earlier.requestedAt(), null);
    }

    private Dispatch firstDispatch(Task task, ProjectEntry project, DispatchCategory category, DispatchRequest request,
            CaptainOverride captainsOverride) {
        return Dispatch.requested(task.id(), category.id(), branchFor(task, project, category), request.spec(),
                expectedChangeOf(project, category, request.spec(), request.expectedFiles()), captainsOverride, clock.now());
    }

    private static ExpectedChange expectedChangeOf(ProjectEntry project, DispatchCategory category, String spec, List<String> files) {
        if (!category.id().isMechanical()) {
            return null;
        }
        if (project.verify() == null) {
            throw new Refusal(project.project().name() + " names no verify command in the project registry, which a mechanical task is checked against.");
        }
        return new ExpectedChange(spec, files);
    }

    private static BranchName branchFor(Task task, ProjectEntry project, DispatchCategory category) {
        BranchKind kind = task.intake().kind() == TaskKind.SCOUT ? BranchKind.RESEARCH : category.branch();
        if (!project.branchKinds().contains(kind)) {
            throw new Refusal(project.project().name() + " takes no " + kind.word() + " branches; its registry entry allows "
                    + project.branchKinds().stream().map(BranchKind::word).sorted().toList() + ".");
        }
        return BranchName.forWork(kind, requireWorkKey(task));
    }

    private static WorkKey requireWorkKey(Task task) {
        WorkKey key = task.intake().workKey();
        if (key == null) {
            throw new Refusal(task.id() + " has no work key, and a branch carries one; give the task its key first.");
        }
        return key;
    }

    private DispatchOutcome carriedOut(Dispatching dispatching, Actor actor) {
        refreshes.requireFresh(dispatching.project());
        if (dispatching.researchFirst().queuesResearch()) {
            return researchFirst(dispatching.dispatch(), actor);
        }
        if (dispatching.researchFirst().countsInThePilot()) {
            research.countWithoutResearch(dispatching.dispatch());
        }
        if (dispatching.isBeyondCapacity()) {
            return transaction.execute(_ -> deferred(dispatching, actor));
        }
        transaction.executeWithoutResult(_ -> awayLimit.requireRoomForAnotherWorker());
        return DispatchOutcome.of(launcher.launch(dispatching.dispatch(), null, actor));
    }

    private DispatchOutcome researchFirst(Dispatch shipDispatch, Actor actor) {
        DispatchRequest planRequest = research.researchRequestFor(shipDispatch);
        Dispatching planning = transaction.execute(_ -> {
            TaskId plan = research.queueThePlanOf(shipDispatch, actor);
            return dispatching(new TaskReference.ById(plan), planRequest);
        });
        return carriedOut(planning, actor).planning(shipDispatch.task());
    }

    private DispatchOutcome deferred(Dispatching dispatching, Actor actor) {
        Dispatch deferred = dispatching.dispatch().deferred(clock.now());
        dispatches.save(deferred);
        int capacity = dispatching.capacity().orElseThrow();
        events.record(new DispatchDeferred(deferred.task(), dispatching.project(), deferred.category(), dispatching.occupied(), capacity),
                actor, deferred.deferredAt());
        return DispatchOutcome.of(new DispatchOutcome.Deferred(deferred.task(), dispatching.project(), dispatching.occupied(), capacity));
    }

    private record Dispatching(Dispatch dispatch, ProjectName project, int occupied, OptionalInt capacity, ResearchFirst researchFirst) {

        boolean isBeyondCapacity() {
            return capacity.isPresent() && occupied >= capacity.getAsInt();
        }
    }
}
