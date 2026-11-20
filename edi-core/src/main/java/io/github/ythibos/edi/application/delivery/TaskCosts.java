package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.Worker;

@Component
public class TaskCosts {

    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final Pipeline pipeline;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TaskCosts(WorkerStore workers, WorkerRuntime runtime, Pipeline pipeline, Clock clock, TransactionTemplate transaction) {
        this.workers = workers;
        this.runtime = runtime;
        this.pipeline = pipeline;
        this.clock = clock;
        this.transaction = transaction;
    }

    public TaskCost countFor(Workplace workplace) {
        List<Worker> workersOfTheTask = transaction.execute(_ -> workers.allOf(workplace.task().id()));
        return new TaskCost(workplace.task().id(), dispatchedRoute(workersOfTheTask), spentBy(workersOfTheTask), spentByThePipelineOn(workplace),
                clock.now());
    }

    private static Route dispatchedRoute(List<Worker> workersOfTheTask) {
        return workersOfTheTask.stream().findFirst().map(Worker::route).orElse(null);
    }

    private TokenUsage spentBy(List<Worker> workersOfTheTask) {
        return earliestLaunchPerTranscript(workersOfTheTask).entrySet().stream()
                .map(transcript -> runtime.tokensSpentIn(transcript.getKey(), transcript.getValue()))
                .reduce(TokenUsage.NONE, TokenUsage::plus);
    }

    private static Map<String, Instant> earliestLaunchPerTranscript(List<Worker> workersOfTheTask) {
        return workersOfTheTask.stream()
                .filter(worker -> Objects.nonNull(worker.transcript()))
                .collect(Collectors.toMap(Worker::transcript, Worker::launchedAt, (first, second) -> first.isBefore(second) ? first : second));
    }

    private TokenUsage spentByThePipelineOn(Workplace workplace) {
        return pipeline.tokensSpentOn(workplace.project().projectClone(), workplace.dispatch().branch()).orElse(null);
    }
}
