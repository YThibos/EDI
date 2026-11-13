package io.github.ythibos.edi.domain.decision;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerQuestion;

public record UndeliveredAnswer(DecisionNumber decision, TaskId task, StatusKey question) {

    public static Optional<UndeliveredAnswer> of(Decision decision, WorkerQuestion question, List<InboxMessage> steers) {
        if (!decision.wasAnsweredSince(question.askedAt()) || question.isResolvedByOneOf(steers)) {
            return Optional.empty();
        }
        return Optional.of(new UndeliveredAnswer(decision.number(), question.task(), question.key()));
    }
}
