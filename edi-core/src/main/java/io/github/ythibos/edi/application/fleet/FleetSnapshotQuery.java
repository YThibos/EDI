package io.github.ythibos.edi.application.fleet;

import static java.util.function.Predicate.not;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.PipelineRunStore;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.task.Blocker;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class FleetSnapshotQuery {

    private static final Duration LANDED_RECENTLY = Duration.ofDays(7);
    private static final Duration ANSWERED_RECENTLY = Duration.ofHours(24);

    private final TaskStore tasks;
    private final DecisionStore decisions;
    private final WorkerStore workers;
    private final PipelineRunStore pipelineRuns;
    private final DeliveryStore deliveries;
    private final InboxStore inbox;
    private final BoardStore boards;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public FleetSnapshotQuery(TaskStore tasks, DecisionStore decisions, WorkerStore workers, PipelineRunStore pipelineRuns, DeliveryStore deliveries,
            InboxStore inbox, BoardStore boards, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.decisions = decisions;
        this.workers = workers;
        this.pipelineRuns = pipelineRuns;
        this.deliveries = deliveries;
        this.inbox = inbox;
        this.boards = boards;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public FleetSnapshot snapshot() {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            List<Task> inFlight = tasks.inState(TaskState.IN_FLIGHT);
            List<Task> queued = tasks.inState(TaskState.QUEUED);
            Map<TaskId, Decision> openDecisions = openDecisionsByTask();
            List<Task> openWork = Stream.concat(inFlight.stream(), queued.stream()).toList();
            return new FleetSnapshot(waitingOnTheCaptain(openDecisions, inFlight, now), ongoing(unheld(inFlight)), parkedAmong(openWork, openDecisions),
                    unheld(queued), landedSince(now.minus(LANDED_RECENTLY)), answeredSince(now.minus(ANSWERED_RECENTLY)));
        });
    }

    private Map<TaskId, Decision> openDecisionsByTask() {
        Map<TaskId, Decision> byTask = new LinkedHashMap<>();
        decisions.open().forEach(decision -> byTask.put(decision.task(), decision));
        return byTask;
    }

    private WaitingOnTheCaptain waitingOnTheCaptain(Map<TaskId, Decision> openDecisions, List<Task> inFlight, Instant now) {
        List<OpenDecision> open = decisionsOf(openDecisions);
        List<WaitingBoard> waitingBoards = waitingBoards(open);
        List<HandedOverRequest> handedOver = handedOverRequestsAmong(inFlight);
        return new WaitingOnTheCaptain(boundToNoWaitingBoard(open, waitingBoards), waitingBoards, readyForReviewAmong(handedOver),
                stillChangingAmong(handedOver), questionsOfWorkersAmong(inFlight, now));
    }

    private List<WorkerQuestion> questionsOfWorkersAmong(List<Task> inFlight, Instant now) {
        return inFlight.stream()
                .flatMap(task -> workers.latestOf(task.id()).filter(not(Worker::isRetired)).filter(Worker::waitsOnThePrimary)
                        .map(worker -> new WorkerQuestion(task, worker, Duration.between(worker.lastStatus().at(), now))).stream())
                .toList();
    }

    private List<OpenDecision> decisionsOf(Map<TaskId, Decision> openDecisions) {
        return openDecisions.values().stream().map(decision -> new OpenDecision(decision, tasks.get(decision.task()))).toList();
    }

    private List<WaitingBoard> waitingBoards(List<OpenDecision> open) {
        return boards.listening().stream()
                .map(board -> new WaitingBoard(board, tasks.get(board.task()), boundBy(board, open)))
                .filter(WaitingBoard::waitsOnTheCaptain)
                .toList();
    }

    private static List<OpenDecision> boundBy(Board board, List<OpenDecision> open) {
        return open.stream().filter(decision -> board.bound().contains(decision.decision().number())).toList();
    }

    private List<HandedOverRequest> handedOverRequestsAmong(List<Task> inFlight) {
        return inFlight.stream()
                .flatMap(task -> handedOverRequestOf(task, doneStands(task)).stream())
                .toList();
    }

    private boolean doneStands(Task task) {
        return workers.latestOf(task.id()).filter(Worker::hadItsDoneAccepted).isPresent();
    }

    private Optional<HandedOverRequest> handedOverRequestOf(Task task, boolean doneStands) {
        return deliveries.of(task.id())
                .filter(Delivery::isOpen)
                .filter(_ -> hadADoneAccepted(task, doneStands))
                .map(delivery -> new HandedOverRequest(task, delivery, doneStands, runningPipelineOf(task), unacknowledgedSteersOf(task)));
    }

    private boolean hadADoneAccepted(Task task, boolean doneStands) {
        return doneStands || events.hadADoneAccepted(task.id());
    }

    private PipelineRun runningPipelineOf(Task task) {
        return pipelineRuns.latestOf(task.id()).filter(PipelineRun::isRunning).orElse(null);
    }

    private List<Integer> unacknowledgedSteersOf(Task task) {
        return inbox.unacknowledgedOf(task.id()).stream().map(InboxMessage::number).toList();
    }

    private static List<OpenDecision> boundToNoWaitingBoard(List<OpenDecision> open, List<WaitingBoard> waitingBoards) {
        return open.stream().filter(decision -> waitingBoards.stream().noneMatch(board -> board.decisions().contains(decision))).toList();
    }

    private static List<ReadyForReview> readyForReviewAmong(List<HandedOverRequest> handedOver) {
        return handedOver.stream().filter(HandedOverRequest::isSettled).map(HandedOverRequest::readyForReview).toList();
    }

    private static List<ChangingRequest> stillChangingAmong(List<HandedOverRequest> handedOver) {
        return handedOver.stream().filter(not(HandedOverRequest::isSettled)).map(HandedOverRequest::stillChanging).toList();
    }

    private static List<Task> unheld(List<Task> work) {
        return work.stream().filter(not(Task::isHeld)).toList();
    }

    private List<OngoingTask> ongoing(List<Task> inFlight) {
        return inFlight.stream()
                .map(task -> new OngoingTask(task, workers.latestOf(task.id()).orElse(null), pipelineRuns.latestOf(task.id()).orElse(null),
                        deliveries.of(task.id()).orElse(null)))
                .toList();
    }

    private List<LandedTask> landedSince(Instant since) {
        return tasks.closedSince(TaskState.DONE, since).stream()
                .filter(FleetSnapshotQuery::isWork)
                .map(task -> new LandedTask(task, deliveries.of(task.id()).orElse(null)))
                .toList();
    }

    private static boolean isWork(Task task) {
        return task.intake().kind() != TaskKind.CAPTAIN;
    }

    private List<RecentAnswer> answeredSince(Instant since) {
        return decisions.answeredSince(since).stream().map(decision -> new RecentAnswer(decision, tasks.get(decision.task()))).toList();
    }

    private List<ParkedTask> parkedAmong(List<Task> openWork, Map<TaskId, Decision> openDecisions) {
        return openWork.stream()
                .filter(Task::isHeld)
                .map(task -> parked(task, openDecisions.get(task.id())))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<ParkedTask> parked(Task heldTask, Decision openDecision) {
        List<Task> blockers = unresolvedBlockersOf(heldTask);
        if (waitsOnlyForItsDecision(heldTask, blockers)) {
            return Optional.empty();
        }
        return Optional.of(new ParkedTask(heldTask, openDecision, blockers));
    }

    private List<Task> unresolvedBlockersOf(Task task) {
        return tasks.blockersOf(task).stream()
                .filter(not(Blocker::isResolved))
                .map(blocker -> tasks.get(blocker.id()))
                .toList();
    }

    private static boolean waitsOnlyForItsDecision(Task heldTask, List<Task> unresolvedBlockers) {
        return heldTask.hold().awaitsADecision() && unresolvedBlockers.isEmpty();
    }

    private record HandedOverRequest(Task task, Delivery delivery, boolean doneStands, PipelineRun runningPipeline, List<Integer> unacknowledgedSteers) {

        boolean isSettled() {
            return doneStands && runningPipeline == null && unacknowledgedSteers.isEmpty();
        }

        ReadyForReview readyForReview() {
            return new ReadyForReview(task, delivery);
        }

        ChangingRequest stillChanging() {
            return new ChangingRequest(task, delivery, !doneStands, runningPipeline, unacknowledgedSteers);
        }
    }
}
