package io.github.ythibos.edi.application.task;

import java.util.List;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.StageReached;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.WorkedTime;

public record TaskDetail(
        Task task,
        List<Blocker> blockers,
        Decision decision,
        Delivery delivery,
        PipelineRun pipelineRun,
        TaskCost cost,
        WorkedTime worked,
        List<StageReached> stages,
        List<RecordedEvent> timeline) {

    public TaskDetail {
        blockers = List.copyOf(blockers);
        stages = List.copyOf(stages);
        timeline = List.copyOf(timeline);
    }
}
