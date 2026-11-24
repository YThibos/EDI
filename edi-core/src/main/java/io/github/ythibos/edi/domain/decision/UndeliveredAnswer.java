package io.github.ythibos.edi.domain.decision;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerQuestion;

public record UndeliveredAnswer(DecisionNumber decision, TaskId task, StatusKey question) {

    public static Optional<UndeliveredAnswer> of(DecisionsOfATask decisions, WorkerQuestion question, List<InboxMessage> steers) {
        return decisions.answering(question)
                .filter(_ -> !question.isResolvedByOneOf(steers))
                .map(answered -> new UndeliveredAnswer(answered.number(), question.task(), question.key()));
    }
}
