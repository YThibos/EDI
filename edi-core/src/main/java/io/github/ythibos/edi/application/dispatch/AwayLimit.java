package io.github.ythibos.edi.application.dispatch;

import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.store.PresenceStore;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;

@Component
public class AwayLimit {

    private final PresenceStore presence;
    private final WorkerPlaces places;

    public AwayLimit(PresenceStore presence, WorkerPlaces places) {
        this.presence = presence;
        this.places = places;
    }

    public void requireRoomForAnotherWorker() {
        mandateAtItsCap().ifPresent(mandate -> {
            throw new Refusal("The captain is away, and meanwhile EDI runs at most " + mandate.workerCap()
                    + " workers, as many as ran when the captain left; this dispatch waits for the captain's return or for a running worker to finish.");
        });
    }

    public boolean hasRoomForAnotherWorker() {
        return mandateAtItsCap().isEmpty();
    }

    private Optional<Mandate> mandateAtItsCap() {
        return presence.current().map(Presence::mandate).filter(mandate -> places.occupied() >= mandate.workerCap());
    }
}
