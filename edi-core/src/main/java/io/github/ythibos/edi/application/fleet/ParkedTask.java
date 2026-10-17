package io.github.ythibos.edi.application.fleet;

import java.util.List;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;

public record ParkedTask(Task task, Decision decision, List<Task> blockers) {

    public ParkedTask {
        blockers = List.copyOf(blockers);
    }

    public boolean isBlocked() {
        return !blockers.isEmpty();
    }

    public String whyParked() {
        if (isBlocked()) {
            return "blocked by " + blockers.stream().map(blocker -> blocker.intake().title()).collect(Collectors.joining(", "));
        }
        return switch (task.hold().until()) {
            case HoldUntil.Day day -> "deferred until " + day.date();
            case HoldUntil.CaptainsWord _ -> "on hold";
        };
    }
}
