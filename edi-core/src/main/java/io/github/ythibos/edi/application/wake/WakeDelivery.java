package io.github.ythibos.edi.application.wake;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.primary.RoleReturn;

public record WakeDelivery(Optional<RoleReturn> roleReturn, List<QueuedWake> wakes, int openDecisions, int ongoing, int outageEntries, Presence presence,
        boolean streamContinues) {

    public WakeDelivery {
        wakes = List.copyOf(wakes);
    }

    public List<Long> numbers() {
        return wakes.stream().map(QueuedWake::number).toList();
    }
}
