package io.github.ythibos.edi.domain.task;

import java.time.Instant;

public record StageReached(Instant at, TaskStage stage, String reason) {
}
