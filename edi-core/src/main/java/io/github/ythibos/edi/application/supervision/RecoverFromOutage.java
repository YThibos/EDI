package io.github.ythibos.edi.application.supervision;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.OutageStore;
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
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.recovery.RecoveryAction;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WorkerHealth;

@Service
public class RecoverFromOutage {

    private final OutageStore outages;
    private final TaskStore tasks;
    private final DispatchStore dispatches;
    private final PrimarySessions primarySessions;
    private final WorkerListQuery workers;
    private final CurrentRoutes routes;
    private final LaunchWorker launcher;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RecoverFromOutage(OutageStore outages, TaskStore tasks, DispatchStore dispatches, PrimarySessions primarySessions,
            WorkerListQuery workers, CurrentRoutes routes, LaunchWorker launcher, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.outages = outages;
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
        Choice choice = transaction.execute(_ -> chosen(number, action, change, actor));
        try {
            return launcher.relaunch(choice.entry().task(), launchKindOf(action), choice.captainsOverride(), choice.entry().cause().sentence(), actor);
        } catch (RuntimeException failed) {
            transaction.executeWithoutResult(_ -> restored(choice, failed.getMessage(), actor));
            throw new Refusal("Number " + number + " on the outage hold list could not be relaunched, so it stays on the list: " + failed.getMessage());
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
            throw new Refusal("The " + limit.name() + " still holds" + (limit.resetsAt() == null ? "" : " until " + limit.resetsAt())
                    + ", so a relaunch now would stop on it again; answer the list once it has reset.");
        });
    }

    private Choice chosen(int number, RecoveryAction action, RouteChange change, Actor actor) {
        Instant now = clock.now();
        OutageEntry entry = outages.numbered(number).orElseThrow(() -> new Refusal("The outage hold list has no number " + number + "."));
        Task task = tasks.get(entry.task());
        if (task.isHeld() && !entry.holds(task.hold())) {
            throw new Refusal(task.id() + " is also held: " + task.hold().reason() + ". Settle that first.");
        }
        if (task.isHeld()) {
            tasks.update(task.released());
            events.record(new TaskReleased(task.id()), actor, now);
        }
        outages.close(entry);
        events.record(new RecoveryChosen(task.id(), number, action), actor, now);
        return new Choice(entry, task.isHeld(), captainsOverrideOf(task, number, change));
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

    private void restored(Choice choice, String because, Actor actor) {
        Instant now = clock.now();
        OutageEntry entry = choice.entry();
        outages.list(entry);
        if (choice.wasHeld()) {
            Task held = tasks.get(entry.task()).held(entry.hold());
            tasks.update(held);
            events.record(new TaskHeld(held.id(), held.hold()), actor, now);
        }
        events.record(new RecoveryFailed(entry.task(), entry.number(), because), Actor.CORE, now);
    }

    public record RouteChange(ModelId model, EffortLevel effort) {

        public boolean isEmpty() {
            return model == null && effort == null;
        }
    }

    private record Choice(OutageEntry entry, boolean wasHeld, CaptainOverride captainsOverride) {
    }
}
