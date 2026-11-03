package io.github.ythibos.edi.application.wake;

import java.util.List;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.domain.presence.Presence;

public record WakeDelivery(List<QueuedWake> wakes, int openDecisions, int ongoing, int outageEntries, Presence presence, boolean streamContinues) {

    public WakeDelivery {
        wakes = List.copyOf(wakes);
    }

    public List<Long> numbers() {
        return wakes.stream().map(QueuedWake::number).toList();
    }
}
