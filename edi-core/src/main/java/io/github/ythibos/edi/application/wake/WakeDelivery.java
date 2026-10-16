package io.github.ythibos.edi.application.wake;

import java.util.List;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;

public record WakeDelivery(List<QueuedWake> wakes, int openDecisions, int ongoing, int outageEntries) {

    public WakeDelivery {
        wakes = List.copyOf(wakes);
    }
}
