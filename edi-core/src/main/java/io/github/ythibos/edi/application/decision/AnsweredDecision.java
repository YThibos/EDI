package io.github.ythibos.edi.application.decision;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;

public record AnsweredDecision(Decision decision, Task task) {
}
