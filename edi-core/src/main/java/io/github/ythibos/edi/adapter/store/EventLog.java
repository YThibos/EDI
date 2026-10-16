package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.event.DomainEvent.DeclaredWaitEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.DeferredLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.DispatchDeferred;
import io.github.ythibos.edi.domain.event.DomainEvent.HoldDateArrived;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeHeldBack;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeTyped;
import io.github.ythibos.edi.domain.event.DomainEvent.LostInOutage;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeAccepted;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryChosen;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryCostRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerAcknowledged;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerNotReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.event.DomainEvent.ToolCallRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerBusyTooLong;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerDied;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerNotified;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerSessionEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStale;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStarted;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopRequested;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.task.CaptainHold;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeRule;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class EventLog {

    private final JdbcClient store;
    private final JsonMapper json;
    private final WakeStore wakes;
    private final ApplicationEventPublisher publisher;

    public EventLog(JdbcClient store, JsonMapper json, WakeStore wakes, ApplicationEventPublisher publisher) {
        this.store = store;
        this.json = json;
        this.wakes = wakes;
        this.publisher = publisher;
    }

    public void record(DomainEvent event, Actor actor, Instant at) {
        long sequence = store.sql("insert into event (at, actor, type, task_id, payload) values (:at, :actor, :type, :task, :payload) returning sequence")
                .param("at", Columns.text(at))
                .param("actor", actor.name())
                .param("type", event.getClass().getSimpleName())
                .param("task", event.task().value())
                .param("payload", json.writeValueAsString(payloadOf(event)))
                .query(Long.class)
                .single();
        WakeRule.wakeFor(event, actor).ifPresent(wake -> queue(sequence, wake, at));
    }

    public long latestSequence() {
        return store.sql("select coalesce(max(sequence), 0) from event").query(Long.class).single();
    }

    public boolean anyAfter(long sequence) {
        return store.sql("select exists (select 1 from event where sequence > :sequence)")
                .param("sequence", sequence)
                .query((row, _) -> row.getBoolean(1))
                .single();
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
            case DispatchDeferred deferred -> payload.put("project", deferred.project().value())
                    .put("category", deferred.category().value())
                    .put("running", deferred.running())
                    .put("capacity", deferred.capacity());
            case DeferredLaunchFailed failed -> payload.put("because", failed.because());
            case TaskDispatched dispatched -> payload.put("worker", dispatched.worker().toString())
                    .put("category", dispatched.category().value())
                    .put("model", dispatched.route().model().value())
                    .put("effort", dispatched.route().namesAnEffort() ? dispatched.route().effort().word() : null)
                    .put("branch", dispatched.branch().toString())
                    .put("because", dispatched.because());
            case WorkerStarted started -> payload.put("worker", started.worker().toString());
            case WorkerLaunchFailed failed -> payload.put("worker", failed.worker().toString()).put("pane", failed.pane().word());
            case WorkerRetired retired -> payload.put("worker", retired.worker().toString()).put("because", retired.because());
            case StatusReported reported -> withStatus(payload.put("worker", Columns.textOf(reported.worker())), reported.report());
            case SteerSent sent -> payload.put("number", sent.number()).put("resolves", Columns.textOf(sent.resolves()));
            case SteerReceived received -> payload.put("number", received.number()).put("proof", received.proof());
            case SteerAcknowledged acknowledged -> payload.put("number", acknowledged.number());
            case SteerNotReceived notReceived -> payload.put("number", notReceived.number());
            case ToolCallRefused refused -> payload.put("worker", refused.worker().toString()).put("rule", refused.rule().word());
            case MechanicalChangeAccepted accepted -> payload.put("worker", accepted.worker().toString());
            case MechanicalChangeRefused refused -> payload.put("worker", refused.worker().toString()).put("because", refused.because());
            case BranchDeliveredToCaptain delivered -> payload.put("branch", delivered.branch().toString());
            case BranchDeliveryFailed failed -> payload.put("branch", failed.branch().toString()).put("because", failed.because());
            case WorkerRelaunched relaunched -> payload.put("worker", relaunched.worker().toString())
                    .put("launch", relaunched.launch().word())
                    .put("model", relaunched.route().model().value())
                    .put("effort", relaunched.route().namesAnEffort() ? relaunched.route().effort().word() : null)
                    .put("because", relaunched.because());
            case WorkerNotified notified -> payload.put("worker", notified.worker().toString()).put("kind", notified.kind());
            case WorkerSessionEnded ended -> payload.put("worker", ended.worker().toString()).put("reason", ended.reason());
            case WorkerStoppedOnAnError stopped -> payload.put("worker", stopped.worker().toString()).put("error", stopped.error());
            case UsageLimitReached reached -> payload.put("worker", reached.worker().toString())
                    .put("limit", reached.limit().name())
                    .put("resetsAt", Columns.text(reached.limit().resetsAt()));
            case LimitResumeTyped typed -> payload.put("worker", typed.worker().toString());
            case LimitResumeHeldBack heldBack -> payload.put("worker", heldBack.worker().toString()).put("pane", heldBack.pane().word());
            case WorkerDied died -> payload.put("worker", died.worker().toString()).put("cause", died.cause().word());
            case WorkerStale stale -> payload.put("worker", stale.worker().toString()).put("quietSince", Columns.text(stale.quietSince()));
            case WorkerBusyTooLong busy -> payload.put("worker", busy.worker().toString()).put("busySince", Columns.text(busy.busySince()));
            case DeclaredWaitEnded ended -> withStatus(payload.put("worker", ended.worker().toString()), ended.pause());
            case LostInOutage lost -> payload.put("worker", lost.worker().toString()).put("number", lost.number()).put("cause", lost.cause().word());
            case RecoveryChosen chosen -> payload.put("number", chosen.number()).put("action", chosen.action().word());
            case RecoveryFailed failed -> payload.put("number", failed.number()).put("because", failed.because());
            case WorkerStopRequested stopped -> payload.put("worker", stopped.worker().toString());
            case RecoveryCostRecorded cost -> withTokens(payload.put("worker", cost.worker().toString()).put("launch", cost.launch().word()),
                    cost.tokens());
            case HoldDateArrived due -> payload.put("until", due.until().toString());
        };
    }

    private void queue(long sequence, Wake wake, Instant at) {
        wakes.enqueue(sequence, wake, at);
        publisher.publishEvent(new WakeQueued(sequence));
    }

    private static ObjectNode withTokens(ObjectNode payload, TokenUsage tokens) {
        return payload.put("input", tokens.input())
                .put("cacheRead", tokens.cacheRead())
                .put("cacheWrite", tokens.cacheWrite())
                .put("output", tokens.output());
    }

    private static ObjectNode withStatus(ObjectNode payload, StatusReport report) {
        return payload.put("state", report.state().word())
                .put("key", Columns.textOf(report.key()))
                .put("until", Columns.text(report.until()))
                .put("text", report.text());
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
