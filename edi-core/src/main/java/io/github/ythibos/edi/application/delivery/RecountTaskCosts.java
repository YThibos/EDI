package io.github.ythibos.edi.application.delivery;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.TaskCostStore;
import io.github.ythibos.edi.application.delivery.RecountedCosts.Recount;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.Pipeline;
import io.github.ythibos.edi.application.worker.WorkerContext;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCostRecounted;
import io.github.ythibos.edi.domain.worker.TokenUsage;

@Service
public class RecountTaskCosts {

    private final TaskCostStore costs;
    private final WorkerContext context;
    private final Pipeline pipeline;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RecountTaskCosts(TaskCostStore costs, WorkerContext context, Pipeline pipeline, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.costs = costs;
        this.context = context;
        this.pipeline = pipeline;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public RecountedCosts recountSince(LocalDate day, Actor actor) {
        List<TaskCost> counted = transaction.execute(_ -> costs.countedSince(startOf(day)));
        List<Recount> changed = counted.stream()
                .flatMap(cost -> recounted(cost).stream())
                .toList();
        changed.forEach(recount -> record(recount, actor));
        return new RecountedCosts(day, counted.size(), changed);
    }

    private Instant startOf(LocalDate day) {
        return day.atStartOfDay(clock.zone()).toInstant();
    }

    private Optional<Recount> recounted(TaskCost cost) {
        return spentByThePipelineOn(cost)
                .map(cost::withPipelineSpend)
                .filter(recount -> !recount.equals(cost))
                .map(recount -> new Recount(cost, recount));
    }

    private Optional<TokenUsage> spentByThePipelineOn(TaskCost cost) {
        Workplace workplace = transaction.execute(_ -> context.of(cost.task()));
        return pipeline.tokensSpentOn(workplace.project().projectClone(), workplace.dispatch().branch());
    }

    private void record(Recount recount, Actor actor) {
        transaction.executeWithoutResult(_ -> {
            costs.updatePipelineSpend(recount.after());
            events.record(new TaskCostRecounted(recount.after().task(), recount.before(), recount.after()), actor, clock.now());
        });
    }
}
