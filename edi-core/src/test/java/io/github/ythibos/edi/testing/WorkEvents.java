package io.github.ythibos.edi.testing;

import java.time.Duration;
import java.time.Instant;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerRelaunched;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStoppedOnAnError;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerTurnEnded;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WorkerId;

public final class WorkEvents {

    public static final MergeRequestAddress DRAFT =
            new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org", "team/service"), 7);

    private final TestHome home;
    private final TaskId task;

    private WorkEvents(TestHome home, TaskId task) {
        this.home = home;
        this.task = task;
    }

    private static final Route ROUTE = new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH);

    public static WorkEvents of(TestHome home, String task) {
        return new WorkEvents(home, new TaskId(task));
    }

    public WorkEvents dispatchedTo(int incarnation, String branch) {
        return recorded(new TaskDispatched(task, new WorkerId(task, incarnation), CategoryId.IMPLEMENTATION,
                ROUTE, BranchName.parse(branch), null), Actor.PRIMARY);
    }

    public WorkEvents reported(StatusState state, String key, String text) {
        StatusReport report = new StatusReport(state, key == null ? null : new StatusKey(key), null, text);
        return recorded(new StatusReported(task, WorkerId.first(task), report), Actor.worker(WorkerId.first(task)));
    }

    public WorkEvents reachedItsUsageLimitUntil(Duration reset) {
        WorkerId worker = WorkerId.first(task);
        return recorded(new UsageLimitReached(task, worker, new UsageLimit("session limit", home.clock().now().plus(reset))), Actor.worker(worker));
    }

    public WorkEvents reachedItsUsageLimitUnnoticedFor(Duration unnoticed) {
        WorkerId worker = WorkerId.first(task);
        Instant stopped = home.clock().now().minus(unnoticed);
        home.events().record(new UsageLimitReached(task, worker, new UsageLimit("session limit", stopped.plus(Duration.ofHours(4)))), Actor.CORE, stopped);
        return this;
    }

    public WorkEvents stoppedOnAnError() {
        WorkerId worker = WorkerId.first(task);
        return recorded(new WorkerStoppedOnAnError(task, worker, "overloaded"), Actor.worker(worker));
    }

    public WorkEvents receivedASteer() {
        return recorded(new SteerReceived(task, 1, "stop hook"), Actor.worker(WorkerId.first(task)));
    }

    public WorkEvents endedItsTurn() {
        return recorded(new WorkerTurnEnded(task, WorkerId.first(task)), Actor.worker(WorkerId.first(task)));
    }

    public WorkEvents endedItsTurnUnnoticedFor(Duration unnoticed) {
        home.events().record(new WorkerTurnEnded(task, WorkerId.first(task)), Actor.CORE, home.clock().now().minus(unnoticed));
        return this;
    }

    public WorkEvents relaunched() {
        return recorded(new WorkerRelaunched(task, WorkerId.first(task), LaunchKind.RESUME, ROUTE, "usage limit reset"), Actor.CORE);
    }

    public WorkEvents after(Duration pause) {
        home.clock().advance(pause);
        return this;
    }

    public WorkEvents deliveredAsADraft() {
        return recorded(new DraftRecorded(task, DRAFT), Actor.CORE);
    }

    public WorkEvents merged() {
        return recorded(new DraftMerged(task, DRAFT, "3333"), Actor.CORE);
    }

    public WorkEvents cleanedUp() {
        return recorded(new TaskCleanedUp(task), Actor.CORE);
    }

    private WorkEvents recorded(DomainEvent event, Actor actor) {
        home.events().record(event, actor, home.clock().now());
        return this;
    }
}
