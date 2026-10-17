package io.github.ythibos.edi.testing;

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
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskDispatched;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
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

    public static WorkEvents of(TestHome home, String task) {
        return new WorkEvents(home, new TaskId(task));
    }

    public WorkEvents dispatchedTo(int incarnation, String branch) {
        return recorded(new TaskDispatched(task, new WorkerId(task, incarnation), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH), BranchName.parse(branch), null), Actor.PRIMARY);
    }

    public WorkEvents reported(StatusState state, String key, String text) {
        StatusReport report = new StatusReport(state, key == null ? null : new StatusKey(key), null, text);
        return recorded(new StatusReported(task, WorkerId.first(task), report), Actor.worker(WorkerId.first(task)));
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
