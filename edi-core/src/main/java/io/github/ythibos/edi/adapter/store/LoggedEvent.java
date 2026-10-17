package io.github.ythibos.edi.adapter.store;

import java.time.Instant;

import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.task.TaskId;
import tools.jackson.databind.JsonNode;

public record LoggedEvent(long sequence, Instant at, Actor actor, String type, TaskId task, JsonNode payload) {

    public boolean isA(Class<? extends DomainEvent> kind) {
        return type.equals(kind.getSimpleName());
    }

    public String text(String field) {
        JsonNode value = payload.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asString();
    }
}
