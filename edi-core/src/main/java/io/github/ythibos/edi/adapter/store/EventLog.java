package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class EventLog {

    private final JdbcClient store;
    private final JsonMapper json;

    public EventLog(JdbcClient store, JsonMapper json) {
        this.store = store;
        this.json = json;
    }

    public void record(DomainEvent event, Actor actor, Instant at) {
        store.sql("insert into event (at, actor, type, task_id, payload) values (:at, :actor, :type, :task, :payload)")
                .param("at", Columns.text(at))
                .param("actor", actor.name())
                .param("type", event.getClass().getSimpleName())
                .param("task", event.task().value())
                .param("payload", json.writeValueAsString(payloadOf(event)))
                .update();
    }

    public List<RecordedEvent> timelineOf(TaskId task) {
        return store.sql("select sequence, at, actor, type from event where task_id = :task order by sequence")
                .param("task", task.value())
                .query((row, _) -> new RecordedEvent(
                        row.getLong("sequence"),
                        Columns.instant(row.getString("at")),
                        new Actor(row.getString("actor")),
                        row.getString("type"),
                        task))
                .list();
    }

    private ObjectNode payloadOf(DomainEvent event) {
        ObjectNode payload = json.createObjectNode();
        return switch (event) {
            case TaskAdded added -> withIntake(payload, added.intake());
            case TaskNoted noted -> payload.put("note", noted.note());
            case TaskHeld held -> withHold(payload, held.hold());
            case DecisionRaised raised -> payload.put("number", raised.number().toString()).put("title", raised.card().title());
            case DecisionAnswered answered -> withAnswer(payload.put("number", answered.number().toString()), answered.answer());
            case TaskReleased _, TaskDone _ -> payload;
        };
    }

    private static ObjectNode withIntake(ObjectNode payload, Intake intake) {
        payload.put("kind", intake.kind().word())
                .put("title", intake.title())
                .put("workKey", Columns.textOf(intake.workKey()))
                .put("base", Columns.textOf(intake.base()));
        ArrayNode projects = payload.putArray("projects");
        intake.projects().forEach(project -> projects.add(project.value()));
        ArrayNode blockers = payload.putArray("blockedBy");
        intake.blockedBy().forEach(blocker -> blockers.add(blocker.value()));
        return payload;
    }

    private static ObjectNode withHold(ObjectNode payload, CaptainHold hold) {
        return payload.put("kind", hold.kind().word()).put("reason", hold.reason()).put("until", Columns.text(hold.until()));
    }

    private static ObjectNode withAnswer(ObjectNode payload, CaptainAnswer answer) {
        return payload.put("verbatim", answer.verbatim())
                .put("option", Columns.textOf(answer.option()))
                .put("interpretation", answer.interpretation())
                .put("channel", answer.channel().word());
    }
}
