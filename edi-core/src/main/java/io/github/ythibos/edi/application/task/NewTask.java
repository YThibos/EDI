package io.github.ythibos.edi.application.task;

import java.util.Objects;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskSize;
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

    public NewTask sizedByItsBacklogItem() {
        return TaskSize.ofBacklogItem(notes).map(this::sizedAs).orElse(this);
    }

    private NewTask sizedAs(TaskSize itemSize) {
        if (intake.size() != null && intake.size() != itemSize) {
            throw new Refusal(Rule.TASK_INTAKE, "The task is given size " + intake.size().word() + ", but the Size row of its backlog item says "
                    + itemSize.word() + "; a task has one size.");
        }
        return new NewTask(chosenId, intake.sized(itemSize), notes, ticket);
    }
}
