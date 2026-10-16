package io.github.ythibos.edi.application.delivery;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.PipelineRunFinished;
import io.github.ythibos.edi.domain.event.DomainEvent.PipelineRunSeen;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class ObservePipelines {

    private static final Logger LOG = LoggerFactory.getLogger(ObservePipelines.class);

    private final WorkerStore workers;
    private final PipelineRunStore runs;
    private final EventLog events;
    private final WorkerContext context;
    private final Pipeline pipeline;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ObservePipelines(WorkerStore workers, PipelineRunStore runs, EventLog events, WorkerContext context, Pipeline pipeline, Clock clock,
            TransactionTemplate transaction) {
        this.workers = workers;
        this.runs = runs;
        this.events = events;
        this.context = context;
        this.pipeline = pipeline;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void observe() {
        workplacesWithAPipeline().forEach(this::observe);
    }

    private List<Workplace> workplacesWithAPipeline() {
        return transaction.execute(_ -> workers.serving().stream()
                .filter(worker -> !worker.handedItsWorkOver())
                .map(Worker::id)
                .map(worker -> context.of(worker.task()))
                .filter(workplace -> workplace.isAShip() && workplace.delivery() == DeliveryMode.DRAFT_REVIEW)
                .filter(workplace -> Files.isDirectory(workplace.worktree()))
                .toList());
    }

    private void observe(Workplace workplace) {
        try {
            pipeline.latestRunOf(workplace.worktree(), workplace.dispatch().branch())
                    .ifPresent(latest -> transaction.executeWithoutResult(_ -> recorded(workplace.task().id(), latest)));
        } catch (ProgramFailure unreadable) {
            LOG.warn("The pipeline of {} could not be read: {}", workplace.task().id(), unreadable.getMessage());
        }
    }

    private void recorded(TaskId task, PipelineRun latest) {
        Optional<PipelineRun> known = runs.latestOf(task);
        if (known.filter(latest::equals).isPresent()) {
            return;
        }
        Instant now = clock.now();
        runs.save(task, latest, now);
        Optional<PipelineRun> sameRun = known.filter(earlier -> earlier.id().equals(latest.id()));
        if (sameRun.isEmpty()) {
            events.record(new PipelineRunSeen(task, latest), Actor.CORE, now);
        }
        if (!latest.isRunning() && sameRun.map(PipelineRun::isRunning).orElse(true)) {
            events.record(new PipelineRunFinished(task, latest), Actor.CORE, now);
        }
    }
}
