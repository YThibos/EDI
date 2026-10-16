package io.github.ythibos.edi.application.task;

import java.util.List;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.Task;

public record TaskDetail(Task task, List<Blocker> blockers, Decision decision, List<RecordedEvent> timeline) {

    public TaskDetail {
        blockers = List.copyOf(blockers);
        timeline = List.copyOf(timeline);
    }
}
