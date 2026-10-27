package io.github.ythibos.edi.application.worker;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.process.ProcessTable;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.HeavyRunsAtOnce;
import io.github.ythibos.edi.domain.worker.HeavyRun;
import io.github.ythibos.edi.domain.worker.HeavyRuns;
import io.github.ythibos.edi.domain.worker.HeavyRuns.WorkerRun;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class NoticeHeavyRunsAtOnce {

    private final WorkerStore workers;
    private final ProcessTable processes;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private volatile HeavyRuns latest = HeavyRuns.NONE;

    public NoticeHeavyRunsAtOnce(WorkerStore workers, ProcessTable processes, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.workers = workers;
        this.processes = processes;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public synchronized void notice() {
        HeavyRuns now = heavyRunsOf(transaction.execute(_ -> workers.serving()));
        if (now.runAtOnce()) {
            now.startedSince(latest).forEach(started -> recordStartedBeside(started, now));
        }
        latest = now;
    }

    public Optional<HeavyRuns> atOnce() {
        HeavyRuns current = latest;
        return current.runAtOnce() ? Optional.of(current) : Optional.empty();
    }

    private HeavyRuns heavyRunsOf(List<Worker> serving) {
        return new HeavyRuns(serving.stream()
                .flatMap(worker -> heaviestRunOf(worker).map(run -> new WorkerRun(worker.id(), run)).stream())
                .toList());
    }

    private Optional<HeavyRun> heaviestRunOf(Worker worker) {
        return processes.argumentsInTree(worker.window().panePid()).stream()
                .flatMap(arguments -> HeavyRun.of(arguments).stream())
                .min(Comparator.naturalOrder());
    }

    private void recordStartedBeside(WorkerRun started, HeavyRuns now) {
        HeavyRunsAtOnce atOnce = new HeavyRunsAtOnce(started.worker().task(), started.worker(), started.run(), now.beside(started));
        transaction.executeWithoutResult(_ -> events.record(atOnce, Actor.CORE, clock.now()));
    }
}
