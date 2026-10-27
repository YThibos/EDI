package io.github.ythibos.edi.adapter.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswerRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardArmed;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardRetired;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardRoundPosted;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.CaptainSaid;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeAppliedToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeNotApplied;
import io.github.ythibos.edi.domain.event.DomainEvent.CleanupRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.CloneRefreshed;
import io.github.ythibos.edi.domain.event.DomainEvent.ContextLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRaised;
import io.github.ythibos.edi.domain.event.DomainEvent.DeclaredWaitEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.DeferredLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.DeliveryRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.DispatchDeferred;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftOpened;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.HeavyRunsAtOnce;
import io.github.ythibos.edi.domain.event.DomainEvent.HoldDateArrived;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeHeldBack;
import io.github.ythibos.edi.domain.event.DomainEvent.LimitResumeTyped;
import io.github.ythibos.edi.domain.event.DomainEvent.LostInOutage;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeAccepted;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.PipelineRunFinished;
import io.github.ythibos.edi.domain.event.DomainEvent.PipelineRunSeen;
import io.github.ythibos.edi.domain.event.DomainEvent.RecheckAsked;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryChosen;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryCostRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.ResearchQueuedFirst;
import io.github.ythibos.edi.domain.event.DomainEvent.ReviewCounted;
import io.github.ythibos.edi.domain.event.DomainEvent.ScoutPromoted;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerAcknowledged;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerNotReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskAdopted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCostRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDone;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDropped;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskHeld;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskImported;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskKeyed;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskReleased;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketAddedToSprint;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketChoiceRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketCreated;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketLinked;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketTransitioned;
import io.github.ythibos.edi.domain.event.DomainEvent.TicketUpdateRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.ToolCallRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkDiscarded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerBusyTooLong;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerDied;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerLaunchFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerNotified;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRetired;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerSessionEnded;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStale;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStarted;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopMissed;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopRequested;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerTurnEnded;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
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
                .query((row, _) -> row.getLong(1))
                .single();
        WakeRule.wakeFor(event, actor).ifPresent(wake -> queue(sequence, wake, at));
        publisher.publishEvent(new EventRecorded(sequence));
    }

    public List<LoggedEvent> after(long sequence, int most) {
        return store.sql("select sequence, at, actor, type, task_id, payload from event where sequence > :sequence order by sequence limit :most")
                .param("sequence", sequence)
                .param("most", most)
                .query((row, _) -> loggedEvent(row))
                .list();
    }

    public List<LoggedEvent> ofTypesSince(Set<String> types, Instant since) {
        return store.sql("select sequence, at, actor, type, task_id, payload from event where type in (:types) order by sequence")
                .param("types", List.copyOf(types))
                .query((row, _) -> loggedEvent(row))
                .list()
                .stream()
                .filter(event -> !event.at().isBefore(since))
                .toList();
    }

    public List<LoggedEvent> ofTask(TaskId task) {
        return store.sql("select sequence, at, actor, type, task_id, payload from event where task_id = :task order by sequence")
                .param("task", task.value())
                .query((row, _) -> loggedEvent(row))
                .list();
    }

    public long latestSequence() {
        return store.sql("select coalesce(max(sequence), 0) from event").query((row, _) -> row.getLong(1)).single();
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

    public boolean hadADoneAccepted(TaskId task) {
        return store.sql("""
                select exists (
                    select 1 from event left join worker on worker.id = json_extract(event.payload, '$.worker')
                    where event.task_id = :task
                      and (event.type = :mechanicalAccepted
                           or event.type = :reported and json_extract(event.payload, '$.state') = :done and worker.category <> :mechanical))""")
                .param("task", task.value())
                .param("mechanicalAccepted", MechanicalChangeAccepted.class.getSimpleName())
                .param("reported", StatusReported.class.getSimpleName())
                .param("done", StatusState.DONE.word())
                .param("mechanical", CategoryId.MECHANICAL.value())
                .query((row, _) -> row.getBoolean(1))
                .single();
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
            case WorkerTurnEnded ended -> payload.put("worker", ended.worker().toString());
            case WorkerStoppedOnAnError stopped -> payload.put("worker", stopped.worker().toString()).put("error", stopped.error());
            case UsageLimitReached reached -> payload.put("worker", reached.worker().toString())
                    .put("limit", reached.limit().name())
                    .put("resetsAt", Columns.text(reached.limit().resetsAt()));
            case ContextLimitReached reached -> payload.put("worker", reached.worker().toString()).put("fullSince", Columns.text(reached.fullSince()));
            case WorkerStopMissed missed -> payload.put("worker", missed.worker().toString()).put("stoppedAt", Columns.text(missed.stoppedAt()))
                    .put("message", missed.message()).put("noticeTyped", missed.noticeTyped());
            case LimitResumeTyped typed -> payload.put("worker", typed.worker().toString());
            case LimitResumeHeldBack heldBack -> payload.put("worker", heldBack.worker().toString()).put("pane", heldBack.pane().word());
            case WorkerDied died -> payload.put("worker", died.worker().toString()).put("cause", died.cause().word());
            case WorkerStale stale -> payload.put("worker", stale.worker().toString()).put("quietSince", Columns.text(stale.quietSince()));
            case WorkerBusyTooLong busy -> payload.put("worker", busy.worker().toString()).put("busySince", Columns.text(busy.busySince()));
            case HeavyRunsAtOnce atOnce -> withEach(payload.put("worker", atOnce.worker().toString()).put("run", atOnce.run().words()), "beside",
                    atOnce.beside().stream().map(other -> other.worker() + " " + other.run().words()).toList());
            case DeclaredWaitEnded ended -> withStatus(payload.put("worker", ended.worker().toString()), ended.pause());
            case LostInOutage lost -> payload.put("worker", lost.worker().toString()).put("number", lost.number()).put("cause", lost.cause().word());
            case RecoveryChosen chosen -> payload.put("number", chosen.number()).put("action", chosen.action().word());
            case RecoveryFailed failed -> payload.put("number", failed.number()).put("because", failed.because());
            case WorkerStopRequested stopped -> payload.put("worker", stopped.worker().toString());
            case RecoveryCostRecorded cost -> withTokens(payload.put("worker", cost.worker().toString()).put("launch", cost.launch().word()),
                    cost.tokens());
            case HoldDateArrived due -> payload.put("until", due.until().toString());
            case DeliveryRefused refused -> withEach(payload.put("worker", refused.worker().toString()), "gaps", refused.gaps());
            case DraftRecorded recorded -> payload.put("url", recorded.address().url());
            case DraftOpened opened -> payload.put("url", opened.address().url());
            case DraftMerged merged -> payload.put("url", merged.address().url()).put("landedCommit", merged.landedCommit());
            case DraftClosed closed -> payload.put("url", closed.address().url());
            case CloneRefreshed refreshed -> payload.put("project", refreshed.project().value()).put("commit", refreshed.commit());
            case ChangeAppliedToCaptain applied -> withEach(payload, "files", applied.files());
            case ChangeNotApplied notApplied -> payload.put("because", notApplied.because());
            case CleanupRefused refused -> withEach(payload, "findings", refused.findings());
            case TaskCleanedUp _ -> payload;
            case TaskCostRecorded recorded -> withCost(payload, recorded.cost());
            case PipelineRunSeen seen -> withRun(payload, seen.run());
            case PipelineRunFinished finished -> withRun(payload, finished.run());
            case TaskKeyed keyed -> payload.put("key", keyed.key().toString());
            case TicketCreated created -> payload.put("key", created.key().toString());
            case TicketChoiceRefused refused -> payload.put("because", refused.because());
            case TicketAddedToSprint added -> payload.put("key", added.key().toString()).put("sprint", added.sprint());
            case TicketLinked linked -> payload.put("key", linked.key().toString()).put("url", linked.url());
            case TicketTransitioned transitioned -> payload.put("key", transitioned.key().toString())
                    .put("from", transitioned.from())
                    .put("to", transitioned.to());
            case TicketUpdateRefused refused -> payload.put("key", refused.key().toString())
                    .put("update", refused.update())
                    .put("because", refused.because());
            case ResearchQueuedFirst queued -> payload.put("research", queued.research().value()).put("category", queued.category().value());
            case ReviewCounted counted -> payload.put("rounds", counted.review().rounds()).put("findings", counted.review().findings());
            case RecheckAsked asked -> payload.put("number", asked.number().toString()).put("note", asked.note());
            case BoardArmed armed -> withEach(payload.put("board", armed.board().value()).put("file", armed.file()), "bound",
                    armed.bound().stream().map(DecisionNumber::toString).toList());
            case BoardRoundPosted posted -> payload.put("board", posted.board().value()).put("link", posted.link()).put("questions", posted.questions());
            case BoardAnswered answered -> payload.put("board", answered.board().value())
                    .put("round", answered.round())
                    .put("prompts", answered.prompts())
                    .put("sessionEnded", answered.sessionEnded());
            case BoardClosed closed -> payload.put("board", closed.board().value());
            case BoardRetired retired -> payload.put("board", retired.board().value());
            case BoardAnswerRefused refused -> payload.put("number", refused.number().toString())
                    .put("board", refused.board().value())
                    .put("because", refused.because());
            case CaptainSaid said -> payload.put("verbatim", said.verbatim());
            case ScoutPromoted promoted -> payload.put("ship", promoted.ship().value());
            case TaskDropped dropped -> withEach(payload.put("captainsWord", dropped.captainsWord()), "discarded", dropped.discarded());
            case WorkDiscarded discarded -> withEach(payload.put("captainsWord", discarded.captainsWord()), "discarded", discarded.discarded());
            case TaskImported imported -> payload.put("oldHome", imported.oldHome());
            case TaskAdopted adopted -> payload.put("branch", adopted.branch().toString())
                    .put("oldCopy", adopted.oldCopy())
                    .put("session", Columns.textOf(adopted.session()));
        };
    }

    private void queue(long sequence, Wake wake, Instant at) {
        wakes.enqueue(sequence, wake, at);
        publisher.publishEvent(new WakeQueued(sequence));
    }

    private LoggedEvent loggedEvent(ResultSet row) throws SQLException {
        return new LoggedEvent(
                row.getLong("sequence"),
                Columns.instant(row.getString("at")),
                new Actor(row.getString("actor")),
                row.getString("type"),
                new TaskId(row.getString("task_id")),
                json.readTree(row.getString("payload")));
    }

    private static ObjectNode withEach(ObjectNode payload, String field, List<String> values) {
        ArrayNode array = payload.putArray(field);
        values.forEach(array::add);
        return payload;
    }

    private ObjectNode withCost(ObjectNode payload, TaskCost cost) {
        withTokens(payload.putObject("workers"), cost.workers());
        cost.pipelineIfKnown().ifPresentOrElse(pipeline -> withTokens(payload.putObject("pipeline"), pipeline), () -> payload.putNull("pipeline"));
        return payload;
    }

    private static ObjectNode withRun(ObjectNode payload, PipelineRun run) {
        return payload.put("run", run.id())
                .put("status", run.status())
                .put("step", run.activeStep())
                .put("head", run.headCommit())
                .put("mergeRequest", run.mergeRequestUrl());
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
