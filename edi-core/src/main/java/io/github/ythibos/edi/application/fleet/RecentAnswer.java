package io.github.ythibos.edi.application.fleet;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.Task;

public record RecentAnswer(Decision decision, Task task) {
}
