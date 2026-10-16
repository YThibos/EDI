package io.github.ythibos.edi.domain.wake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveredToCaptain;
import io.github.ythibos.edi.domain.event.DomainEvent.BranchDeliveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.ChangeNotApplied;
import io.github.ythibos.edi.domain.event.DomainEvent.CleanupRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.DeliveryRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftMerged;
import io.github.ythibos.edi.domain.event.DomainEvent.DraftRecorded;
import io.github.ythibos.edi.domain.event.DomainEvent.LostInOutage;
import io.github.ythibos.edi.domain.event.DomainEvent.MechanicalChangeAccepted;
import io.github.ythibos.edi.domain.event.DomainEvent.RecoveryFailed;
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerNotReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerReceived;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskCleanedUp;
import io.github.ythibos.edi.domain.event.DomainEvent.TaskNoted;
import io.github.ythibos.edi.domain.event.DomainEvent.UsageLimitReached;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerDied;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStale;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.WorkerId;

class WakeRuleTest {

    private static final TaskId TASK = new TaskId("fit-lamp");
    private static final WorkerId WORKER = WorkerId.first(TASK);
    private static final Actor THE_WORKER = Actor.worker(WORKER);
    private static final Instant RESET = Instant.parse("2026-10-16T14:10:00Z");

    @Test
    void wakesThePrimaryForAStatusThatNeedsItsAction() {
        assertThat(WakeRule.wakeFor(status(StatusState.NEEDS_DECISION, new StatusKey("lamp")), THE_WORKER))
                .contains(new Wake(WakeKind.NEEDS_DECISION, TASK, WORKER, "(lamp) The lamp, needs-decision.", null));
        assertThat(WakeRule.wakeFor(status(StatusState.DONE, null), THE_WORKER)).map(Wake::kind).contains(WakeKind.DONE);
        assertThat(WakeRule.wakeFor(status(StatusState.BLOCKED, null), THE_WORKER)).map(Wake::kind).contains(WakeKind.BLOCKED);
        assertThat(WakeRule.wakeFor(status(StatusState.FAILED, null), THE_WORKER)).map(Wake::kind).contains(WakeKind.FAILED);
        assertThat(WakeRule.wakeFor(status(StatusState.WORKING, null), THE_WORKER)).isEmpty();
        assertThat(WakeRule.wakeFor(status(StatusState.PAUSED, null), THE_WORKER)).isEmpty();
    }

    @Test
    void wakesThePrimaryForWhatSupervisionNotices() {
        assertThat(WakeRule.wakeFor(new WorkerDied(TASK, WORKER, DeathCause.SERVER_CHANGED), Actor.CORE))
                .contains(new Wake(WakeKind.WORKER_DIED, TASK, WORKER, "its tmux server changed", null));
        assertThat(WakeRule.wakeFor(new LostInOutage(TASK, WORKER, 2, DeathCause.MACHINE_RESTARTED), Actor.CORE))
                .contains(new Wake(WakeKind.LOST_IN_OUTAGE, TASK, WORKER, "2: the machine restarted", null));
        assertThat(WakeRule.wakeFor(new UsageLimitReached(TASK, WORKER, new UsageLimit("session limit", RESET)), THE_WORKER))
                .contains(new Wake(WakeKind.USAGE_LIMIT, TASK, WORKER, "session limit", RESET));
        assertThat(WakeRule.wakeFor(new WorkerStale(TASK, WORKER, RESET), Actor.CORE)).contains(new Wake(WakeKind.WORKER_STALE, TASK, WORKER, "", RESET));
    }

    @Test
    void wakesThePrimaryForWhatHappensToADelivery() {
        MergeRequestAddress draft = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);

        assertThat(WakeRule.wakeFor(new DraftMerged(TASK, draft, "3333"), Actor.CORE))
                .contains(new Wake(WakeKind.DRAFT_MERGED, TASK, null, draft.url(), null));
        assertThat(WakeRule.wakeFor(new DraftClosed(TASK, draft), Actor.CORE)).map(Wake::kind).contains(WakeKind.DRAFT_CLOSED);
        assertThat(WakeRule.wakeFor(new BranchDeliveryFailed(TASK, BranchName.parse("feature/HRN-3"), "the fetch was refused"), Actor.CORE))
                .contains(new Wake(WakeKind.DELIVERY_FAILED, TASK, null, "the fetch was refused", null));
        assertThat(WakeRule.wakeFor(new ChangeNotApplied(TASK, "the checkout is dirty"), Actor.CORE)).map(Wake::kind).contains(WakeKind.DELIVERY_FAILED);
        assertThat(WakeRule.wakeFor(new CleanupRefused(TASK, List.of("lamp.txt is not committed", "a commit is not in origin/main")), Actor.CORE))
                .contains(new Wake(WakeKind.CLEANUP_REFUSED, TASK, null, "lamp.txt is not committed; a commit is not in origin/main", null));
        assertThat(WakeRule.wakeFor(new CleanupRefused(TASK, List.of("lamp.txt is not committed")), Actor.PRIMARY)).isEmpty();
        assertThat(WakeRule.wakeFor(new DeliveryRefused(TASK, WORKER, List.of("not a draft")), Actor.CORE)).isEmpty();
        assertThat(WakeRule.wakeFor(new DraftRecorded(TASK, draft), Actor.CORE)).isEmpty();
        assertThat(WakeRule.wakeFor(new TaskCleanedUp(TASK), Actor.CORE)).isEmpty();
    }

    @Test
    void neverWakesThePrimaryForRoutineEventsOrForWhatThePrimaryDidItself() {
        assertThat(WakeRule.wakeFor(new SteerReceived(TASK, 1, "stop hook"), THE_WORKER)).isEmpty();
        assertThat(WakeRule.wakeFor(new SteerNotReceived(TASK, 1), Actor.CORE)).isEmpty();
        assertThat(WakeRule.wakeFor(new MechanicalChangeAccepted(TASK, WORKER), Actor.CORE)).isEmpty();
        assertThat(WakeRule.wakeFor(new BranchDeliveredToCaptain(TASK, BranchName.parse("feature/HRN-14")), Actor.CORE)).isEmpty();
        assertThat(WakeRule.wakeFor(new RecoveryFailed(TASK, 2, "the copy is missing"), Actor.CORE)).isEmpty();
        assertThat(WakeRule.wakeFor(new TaskNoted(TASK, "A note."), Actor.PRIMARY)).isEmpty();
        assertThat(WakeRule.wakeFor(status(StatusState.DONE, null), Actor.PRIMARY)).isEmpty();
        assertThat(WakeRule.wakeFor(new WorkerDied(TASK, WORKER, DeathCause.WINDOW_CLOSED), Actor.PRIMARY)).isEmpty();
    }

    private static StatusReported status(StatusState state, StatusKey key) {
        return new StatusReported(TASK, WORKER, new StatusReport(state, key, null, "The lamp, " + state.word() + "."));
    }
}
