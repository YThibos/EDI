package io.github.ythibos.edi.application.task;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.adapter.store.TaskCostStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.task.Task;

@Service
public class TaskDetailQuery {

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final DeliveryStore deliveries;
    private final PipelineRunStore pipelineRuns;
    private final TaskCostStore costs;
    private final WorkedTimes workedTimes;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TaskDetailQuery(TaskStore tasks, DecisionStore decisions, DeliveryStore deliveries, PipelineRunStore pipelineRuns, TaskCostStore costs,
            WorkedTimes workedTimes, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.deliveries = deliveries;
        this.pipelineRuns = pipelineRuns;
        this.costs = costs;
        this.workedTimes = workedTimes;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public TaskDetail detail(TaskReference reference) {
        return transaction.execute(_ -> {
            Task task = tasks.get(reference);
            return new TaskDetail(
                    task,
                    tasks.blockersOf(task),
                    decisions.ofTask(task.id()).orElse(null),
                    deliveries.of(task.id()).orElse(null),
                    pipelineRuns.latestOf(task.id()).orElse(null),
                    costs.of(task.id()).orElse(null),
                    workedTimes.of(task.id(), measuredUntil(task)).orElse(null),
                    events.stagesOf(task.id()),
                    events.timelineOf(task.id()));
        });
    }

    private Instant measuredUntil(Task task) {
        return task.isClosed() ? task.closed() : clock.now();
    }
}
