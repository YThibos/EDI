package io.github.ythibos.edi.application.task;

import java.util.Objects;

import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.TicketDetails;

public record NewTask(TaskId chosenId, Intake intake, String notes, TicketDetails ticket) {

    public NewTask {
        Objects.requireNonNull(intake, "A new task has its intake.");
        notes = Objects.requireNonNullElse(notes, "");
        ticket = Objects.requireNonNullElse(ticket, TicketDetails.NONE);
    }

    public NewTask(TaskId chosenId, Intake intake, String notes) {
        this(chosenId, intake, notes, TicketDetails.NONE);
    }
}
