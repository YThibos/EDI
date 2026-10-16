package io.github.ythibos.edi.application.dispatch;

import java.util.List;
import java.util.OptionalInt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.event.Actor;

@Service
public class LaunchDeferredDispatches {

    private final DispatchStore dispatches;
    private final TaskStore tasks;
    private final ProjectRegistry registry;
    private final WorkerPlaces places;
    private final LaunchWorker launcher;
    private final TransactionTemplate transaction;

    public LaunchDeferredDispatches(DispatchStore dispatches, TaskStore tasks, ProjectRegistry registry, WorkerPlaces places,
            LaunchWorker launcher, TransactionTemplate transaction) {
        this.dispatches = dispatches;
        this.tasks = tasks;
        this.registry = registry;
        this.places = places;
        this.launcher = launcher;
        this.transaction = transaction;
    }

    public List<Launched> launchWhereThereIsRoom() {
        return transaction.execute(_ -> dispatches.deferred()).stream()
                .filter(this::hasRoom)
                .map(dispatch -> launcher.launch(dispatch, "a worker place on its project came free", Actor.CORE))
                .toList();
    }

    private boolean hasRoom(Dispatch dispatch) {
        return transaction.execute(_ -> {
            ProjectEntry project = registry.entryOf(tasks.get(dispatch.task()).intake().projects().getFirst());
            OptionalInt capacity = project.workerCapacity();
            return capacity.isEmpty() || places.occupiedOn(project.project().name()) < capacity.getAsInt();
        });
    }
}
