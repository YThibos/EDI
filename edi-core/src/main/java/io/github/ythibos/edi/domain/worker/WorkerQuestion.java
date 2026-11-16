package io.github.ythibos.edi.domain.worker;

import java.time.Instant;
import java.util.List;

import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record WorkerQuestion(TaskId task, StatusKey key, Instant askedAt) {

    public boolean isResolvedByOneOf(List<InboxMessage> steers) {
        return steers.stream().anyMatch(this::isResolvedBy);
    }

    private boolean isResolvedBy(InboxMessage steer) {
        return key.equals(steer.resolves()) && !steer.createdAt().isBefore(askedAt);
    }
}
