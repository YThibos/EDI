package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.delivery.CloneRefreshes;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DeferredLaunchFailed;

@Service
public class LaunchDeferredDispatches {

    private final DispatchStore dispatches;
    private final TaskStore tasks;
    private final ProjectRegistry registry;
    private final WorkerPlaces places;
    private final AwayLimit awayLimit;
    private final LaunchWorker launcher;
    private final CloneRefreshes refreshes;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public LaunchDeferredDispatches(DispatchStore dispatches, TaskStore tasks, ProjectRegistry registry, WorkerPlaces places, AwayLimit awayLimit,
            LaunchWorker launcher, CloneRefreshes refreshes, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.dispatches = dispatches;
        this.tasks = tasks;
        this.registry = registry;
        this.places = places;
        this.awayLimit = awayLimit;
        this.launcher = launcher;
        this.refreshes = refreshes;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<Launched> launchWhereThereIsRoom() {
        return transaction.execute(_ -> dispatches.deferred()).stream()
                .filter(this::hasRoom)
                .filter(this::hasAFreshClone)
                .flatMap(dispatch -> attemptedLaunchOf(dispatch).stream())
                .toList();
    }

    private boolean hasRoom(Dispatch dispatch) {
        return transaction.execute(_ -> {
            ProjectEntry project = registry.entryOf(tasks.get(dispatch.task()).intake().projects().getFirst());
            return places.hasRoomOn(project) && awayLimit.hasRoomForAnotherWorker();
        });
    }

    private boolean hasAFreshClone(Dispatch dispatch) {
        return refreshes.isFresh(transaction.execute(_ -> tasks.get(dispatch.task()).intake().projects().getFirst()));
    }

    private Optional<Launched> attemptedLaunchOf(Dispatch dispatch) {
        try {
            return Optional.of(launcher.launch(dispatch, "a worker place on its project came free", Actor.CORE));
        } catch (RuntimeException failed) {
            withdrawTheDeferral(dispatch, failed.getMessage());
            return Optional.empty();
        }
    }

    private void withdrawTheDeferral(Dispatch dispatch, String because) {
        transaction.executeWithoutResult(_ -> {
            dispatches.save(dispatch.undeferred());
            events.record(new DeferredLaunchFailed(dispatch.task(), because), Actor.CORE, clock.now());
        });
    }
}
