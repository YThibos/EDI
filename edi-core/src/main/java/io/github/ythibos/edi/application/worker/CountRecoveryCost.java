package io.github.ythibos.edi.application.worker;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.RecoveryCostStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryCostRecorded;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class CountRecoveryCost {

    private final RecoveryCostStore costs;
    private final EventLog events;
    private final WorkerRuntime runtime;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CountRecoveryCost(RecoveryCostStore costs, EventLog events, WorkerRuntime runtime, Clock clock, TransactionTemplate transaction) {
        this.costs = costs;
        this.events = events;
        this.runtime = runtime;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void countWhenARecovery(Worker worker) {
        if (!worker.launch().kind().isARecovery() || Boolean.TRUE.equals(transaction.execute(_ -> costs.isCounted(worker.id())))) {
            return;
        }
        TokenUsage tokens = runtime.tokensSpentIn(worker.transcript(), worker.launchedAt());
        transaction.executeWithoutResult(_ -> {
            if (!costs.isCounted(worker.id())) {
                Instant now = clock.now();
                costs.count(worker.id(), tokens, now);
                events.record(new RecoveryCostRecorded(worker.id().task(), worker.id(), worker.launch().kind(), tokens), Actor.CORE, now);
            }
        });
    }
}
