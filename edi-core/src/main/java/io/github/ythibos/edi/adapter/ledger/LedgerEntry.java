package io.github.ythibos.edi.adapter.ledger;

import java.time.Instant;

import io.github.ythibos.edi.domain.task.TaskId;

public sealed interface LedgerEntry {

    TaskId task();

    Instant at();

    record Dispatched(TaskId task, Instant at, String kind, String project, String model, String key) implements LedgerEntry {
    }

    record Status(TaskId task, Instant at, String state, String key, String text) implements LedgerEntry {
    }

    record DraftReady(TaskId task, Instant at, String url) implements LedgerEntry {
    }

    record Merged(TaskId task, Instant at, String url) implements LedgerEntry {
    }

    record CleanedUp(TaskId task, Instant at) implements LedgerEntry {
    }
}
