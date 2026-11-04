package io.github.ythibos.edi.application.worker;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.OpenWorkerQuestions.OpenQuestion;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class OpenWorkerQuestionsQuery {

    private final WorkerStore workers;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OpenWorkerQuestionsQuery(WorkerStore workers, Clock clock, TransactionTemplate transaction) {
        this.workers = workers;
        this.clock = clock;
        this.transaction = transaction;
    }

    public OpenWorkerQuestions open() {
        Instant now = clock.now();
        return transaction.execute(_ -> new OpenWorkerQuestions(workers.serving().stream()
                .filter(Worker::waitsOnThePrimary)
                .map(worker -> new OpenQuestion(worker.id(), worker.lastStatus().report(), Duration.between(worker.lastStatus().at(), now)))
                .toList()));
    }
}
