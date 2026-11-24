package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DeferredLaunchFailed;

@Service
public class LaunchDeferredDispatches {

    private final DispatchStore dispatches;
    private final TaskStore tasks;
    private final RoomForAWorker room;
    private final LaunchWorker launcher;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public LaunchDeferredDispatches(DispatchStore dispatches, TaskStore tasks, RoomForAWorker room, LaunchWorker launcher, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.dispatches = dispatches;
        this.tasks = tasks;
        this.room = room;
        this.launcher = launcher;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public List<Launched> launchWhereThereIsRoom() {
        return transaction.execute(_ -> dispatches.deferred()).stream()
                .filter(this::hasRoom)
                .flatMap(dispatch -> attemptedLaunchOf(dispatch).stream())
                .toList();
    }

    private boolean hasRoom(Dispatch dispatch) {
        return room.on(transaction.execute(_ -> tasks.get(dispatch.task()).intake().projects().getFirst()));
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
