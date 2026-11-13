package io.github.ythibos.edi.application.decision;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.domain.decision.UndeliveredAnswer;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerQuestion;

@Service
public class UndeliveredAnswersQuery {

    private final WorkerStore workers;
    private final DecisionStore decisions;
    private final InboxStore inbox;
    private final TransactionTemplate transaction;

    public UndeliveredAnswersQuery(WorkerStore workers, DecisionStore decisions, InboxStore inbox, TransactionTemplate transaction) {
        this.workers = workers;
        this.decisions = decisions;
        this.inbox = inbox;
        this.transaction = transaction;
    }

    public List<UndeliveredAnswer> waiting() {
        return transaction.execute(_ -> workers.serving().stream()
                .map(Worker::openQuestion)
                .flatMap(Optional::stream)
                .map(this::undeliveredAnswerTo)
                .flatMap(Optional::stream)
                .toList());
    }

    private Optional<UndeliveredAnswer> undeliveredAnswerTo(WorkerQuestion question) {
        return decisions.ofTask(question.task()).flatMap(decision -> UndeliveredAnswer.of(decision, question, inbox.resolvingOf(question.task())));
    }
}
