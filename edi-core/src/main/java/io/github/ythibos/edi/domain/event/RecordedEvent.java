package io.github.ythibos.edi.domain.event;

import java.time.Instant;

import io.github.ythibos.edi.domain.task.TaskId;

public record RecordedEvent(long sequence, Instant at, Actor actor, String type, TaskId task) {
}
