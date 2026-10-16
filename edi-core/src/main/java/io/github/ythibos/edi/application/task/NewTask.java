package io.github.ythibos.edi.application.task;

import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;

public record NewTask(TaskId chosenId, Intake intake, String notes) {
}
