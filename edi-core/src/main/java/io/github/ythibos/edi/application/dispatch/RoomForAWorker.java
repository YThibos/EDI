package io.github.ythibos.edi.application.dispatch;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.application.delivery.CloneRefreshes;
import io.github.ythibos.edi.domain.project.ProjectName;

@Component
public class RoomForAWorker {

    private final ProjectRegistry registry;
    private final WorkerPlaces places;
    private final AwayLimit awayLimit;
    private final CloneRefreshes refreshes;
    private final TransactionTemplate transaction;

    public RoomForAWorker(ProjectRegistry registry, WorkerPlaces places, AwayLimit awayLimit, CloneRefreshes refreshes, TransactionTemplate transaction) {
        this.registry = registry;
        this.places = places;
        this.awayLimit = awayLimit;
        this.refreshes = refreshes;
        this.transaction = transaction;
    }

    public boolean on(ProjectName project) {
        return hasAPlaceOn(project) && refreshes.isFresh(project);
    }

    private boolean hasAPlaceOn(ProjectName project) {
        return transaction.execute(_ -> places.roomOn(registry.entryOf(project)).hasRoom() && awayLimit.hasRoomForAnotherWorker());
    }
}
