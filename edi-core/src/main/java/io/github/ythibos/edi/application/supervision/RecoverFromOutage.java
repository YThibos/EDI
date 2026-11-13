package io.github.ythibos.edi.application.supervision;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.dispatch.LaunchWorker;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.primary.PrimarySessions;
import io.github.ythibos.edi.application.worker.WorkerListQuery;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CaptainOverride;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryChosen;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryFailed;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.recovery.RecoveryAction;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WorkerHealth;

@Service
public class RecoverFromOutage {

    private final LeaveTheOutageList outageList;
    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final PrimarySessions primarySessions;
    private final WorkerListQuery workers;
    private final CurrentRoutes routes;
    private final LaunchWorker launcher;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RecoverFromOutage(LeaveTheOutageList outageList, TaskStore tasks, DispatchStore dispatches, PrimarySessions primarySessions,
            WorkerListQuery workers, CurrentRoutes routes, LaunchWorker launcher, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.outageList = outageList;
        this.tasks = tasks;
        this.dispatches = dispatches;
        this.primarySessions = primarySessions;
        this.workers = workers;
        this.routes = routes;
        this.launcher = launcher;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public synchronized Launched answer(int number, RecoveryAction action, RouteChange change, Actor actor) {
        requireNoUsageLimitInForce();
        OutageEntry entry = listed(number);
        CaptainOverride captainsOverride = transaction.execute(_ -> chosen(entry, action, change, actor));
        try {
            return launcher.relaunch(entry.task(), launchKindOf(action), captainsOverride, entry, entry.cause().sentence(), actor);
        } catch (RuntimeException failed) {
            transaction.executeWithoutResult(_ -> events.record(new RecoveryFailed(entry.task(), number, failed.getMessage()), Actor.CORE, clock.now()));
            throw new Refusal(Rule.OUTAGE_LIST, "Number " + number + " on the outage hold list could not be relaunched, so it stays on the list: "
                    + failed.getMessage());
        }
    }

    private void requireNoUsageLimitInForce() {
        Instant now = clock.now();
        Optional<UsageLimit> inForce = workers.serving().entries().stream()
                .flatMap(entry -> entry.health() instanceof WorkerHealth.LimitParked parked ? Stream.of(parked.limit()) : Stream.empty())
                .filter(limit -> limit.holdsAt(now))
                .findFirst()
                .or(() -> primarySessions.holder().map(PrimarySession::limit).filter(limit -> limit.holdsAt(now)));
        inForce.ifPresent(limit -> {
            throw new Refusal(Rule.OUTAGE_LIST, "The " + limit.name() + " still holds" + (limit.resetsAt() == null ? "" : " until " + limit.resetsAt())
                    + ", so a relaunch now would stop on it again; answer the list once it has reset.");
        });
    }

    private OutageEntry listed(int number) {
        return transaction.execute(_ -> outageList.stillListed(number, clock.now()))
                .orElseThrow(() -> new Refusal(Rule.OUTAGE_LIST, "The outage hold list has no number " + number + "."));
    }

    private CaptainOverride chosen(OutageEntry entry, RecoveryAction action, RouteChange change, Actor actor) {
        Task task = tasks.get(entry.task());
        if (task.isHeld() && !entry.holds(task.hold())) {
            throw new Refusal(Rule.TASK_HOLD, task.id() + " is also held: " + task.hold().reason() + ". Settle that first.");
        }
        events.record(new RecoveryChosen(task.id(), entry.number(), action), actor, clock.now());
        return captainsOverrideOf(task, entry.number(), change);
    }

    private CaptainOverride captainsOverrideOf(Task task, int number, RouteChange change) {
        if (change == null || change.isEmpty()) {
            return null;
        }
        Route now = routes.of(task, dispatches.of(task.id()).orElseThrow());
        Route changed = new Route(change.model() == null ? now.model() : change.model(), change.effort() == null ? now.effort() : change.effort());
        return new CaptainOverride(changed, "the captain's answer to number " + number + " on the outage hold list");
    }

    private static LaunchKind launchKindOf(RecoveryAction action) {
        return switch (action) {
            case RESUME -> LaunchKind.RESUME;
            case FRESH -> LaunchKind.FRESH;
        };
    }

    public record RouteChange(ModelId model, EffortLevel effort) {

        public boolean isEmpty() {
            return model == null && effort == null;
        }
    }
}
