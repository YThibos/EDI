package io.github.ythibos.edi.domain.inbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import io.github.ythibos.edi.domain.OperationalText;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record InboxMessage(
        TaskId task,
        int number,
        String body,
        StatusKey resolves,
        Instant createdAt,
        Instant receivedAt,
        Instant acknowledgedAt,
        Instant reportedUnreceivedAt) {

    public InboxMessage {
        Objects.requireNonNull(task, "A message is for a task's worker.");
        body = Objects.requireNonNullElse(body, "").strip();
        if (number < 1) {
            throw new IllegalArgumentException("A task's messages are numbered from 1: " + number);
        }
        if (body.isEmpty()) {
            throw new IllegalArgumentException("A message to a worker says something.");
        }
    }

    public static InboxMessage sent(TaskId task, int number, String body, StatusKey resolves, Instant at) {
        return new InboxMessage(task, number, body, resolves, at, null, null, null);
    }

    public String notice() {
        return OperationalText.typed("message " + number + " waiting: run edi inbox");
    }

    public boolean isNamedIn(String prompt) {
        return prompt.contains(notice());
    }

    public InboxMessage received(Instant at) {
        return isReceived() ? this : new InboxMessage(task, number, body, resolves, createdAt, at, acknowledgedAt, reportedUnreceivedAt);
    }

    public InboxMessage acknowledged(Instant at) {
        if (isAcknowledged()) {
            throw new Refusal("Message " + number + " on " + task + " is already acknowledged.");
        }
        return new InboxMessage(task, number, body, resolves, createdAt, Objects.requireNonNullElse(receivedAt, at), at, reportedUnreceivedAt);
    }

    public InboxMessage reportedUnreceived(Instant at) {
        return new InboxMessage(task, number, body, resolves, createdAt, receivedAt, acknowledgedAt, at);
    }

    public boolean isReceived() {
        return receivedAt != null;
    }

    public boolean isAcknowledged() {
        return acknowledgedAt != null;
    }

    public boolean isOverdue(Instant now, Duration patience) {
        return !isReceived() && reportedUnreceivedAt == null && !now.isBefore(createdAt.plus(patience));
    }
}
