package io.github.ythibos.edi.application.worker;

import java.util.List;

import io.github.ythibos.edi.domain.worker.Worker;

public record WorkerList(List<Entry> entries) {

    public WorkerList {
        entries = List.copyOf(entries);
    }

    public record Entry(Worker worker, boolean windowOpen) {
    }
}
