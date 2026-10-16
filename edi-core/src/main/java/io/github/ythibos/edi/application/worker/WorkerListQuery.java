package io.github.ythibos.edi.application.worker;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.machine.MachineBoot;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.port.WorkerRuntime;
import io.github.ythibos.edi.domain.worker.SupervisionRules;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class WorkerListQuery {

    private final WorkerStore workers;
    private final WorkerRuntime runtime;
    private final MachineBoot boot;
    private final MachineProfile machine;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public WorkerListQuery(WorkerStore workers, WorkerRuntime runtime, MachineBoot boot, MachineProfile machine, Clock clock,
            TransactionTemplate transaction) {
        this.workers = workers;
        this.runtime = runtime;
        this.boot = boot;
        this.machine = machine;
        this.clock = clock;
        this.transaction = transaction;
    }

    public WorkerList serving() {
        List<Worker> serving = transaction.execute(_ -> workers.serving());
        SupervisionRules rules = machine.supervision();
        Instant now = clock.now();
        return new WorkerList(serving.stream().map(worker -> judged(worker, rules, now)).toList());
    }

    public WorkerList.Entry judged(Worker worker) {
        return judged(worker, machine.supervision(), clock.now());
    }

    private WorkerList.Entry judged(Worker worker, SupervisionRules rules, Instant now) {
        return new WorkerList.Entry(worker, rules.healthOf(worker, runtime.check(worker.window()), boot.current(), now));
    }
}
