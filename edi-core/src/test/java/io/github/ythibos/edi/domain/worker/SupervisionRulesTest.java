package io.github.ythibos.edi.domain.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;

class SupervisionRulesTest {

    private static final Instant LAUNCHED = Instant.parse("2026-10-16T09:00:00Z");
    private static final BootId BOOT = new BootId("boot-1");
    private static final SupervisionRules RULES = new SupervisionRules(Duration.ofMinutes(20), Duration.ofHours(4), Duration.ofHours(3),
            Duration.ofMinutes(10));

    @Test
    void readsHealthFromTheHooksTheWindowAndTheBoot() {
        Worker idle = launched().started(null, null, LAUNCHED.plusSeconds(5));
        Worker busy = idle.promptSubmitted(LAUNCHED.plusSeconds(6));

        assertThat(health(launched(), WindowCheck.OPEN, BOOT)).isEqualTo(new WorkerHealth.Starting(LAUNCHED));
        assertThat(health(idle, WindowCheck.OPEN, BOOT)).isEqualTo(new WorkerHealth.Idle(LAUNCHED.plusSeconds(5)));
        assertThat(health(busy, WindowCheck.OPEN, BOOT)).isEqualTo(new WorkerHealth.Busy(LAUNCHED.plusSeconds(6)));
        assertThat(health(busy, WindowCheck.SERVER_CHANGED, BOOT)).isEqualTo(new WorkerHealth.Dead(DeathCause.SERVER_CHANGED));
        assertThat(health(busy, WindowCheck.SERVER_GONE, BOOT)).isEqualTo(new WorkerHealth.Dead(DeathCause.SERVER_GONE));
        assertThat(health(busy, WindowCheck.WINDOW_CLOSED, BOOT)).isEqualTo(new WorkerHealth.Dead(DeathCause.WINDOW_CLOSED));
        assertThat(health(busy, WindowCheck.OPEN, new BootId("boot-2"))).isEqualTo(new WorkerHealth.Dead(DeathCause.MACHINE_RESTARTED));
        assertThat(health(busy.stopRequested(LAUNCHED.plusSeconds(9)), WindowCheck.WINDOW_CLOSED, BOOT)).isEqualTo(new WorkerHealth.Stopped(LAUNCHED.plusSeconds(9)));
        assertThat(health(busy.reported(report(StatusState.DONE, null), LAUNCHED), WindowCheck.WINDOW_CLOSED, BOOT)).isEqualTo(new WorkerHealth.Finished());
        assertThat(health(busy.reported(report(StatusState.DONE, null), LAUNCHED), WindowCheck.OPEN, new BootId("boot-2"))).isEqualTo(new WorkerHealth.Finished());
        assertThat(health(launched().launchFailed(LAUNCHED.plusSeconds(120)), WindowCheck.WINDOW_CLOSED, new BootId("boot-2")))
                .isEqualTo(new WorkerHealth.LaunchFailed(LAUNCHED.plusSeconds(120)));
        assertThat(health(launched().launchFailed(LAUNCHED.plusSeconds(120)).started(null, null, LAUNCHED.plusSeconds(180)), WindowCheck.WINDOW_CLOSED, BOOT))
                .isEqualTo(new WorkerHealth.Dead(DeathCause.WINDOW_CLOSED));
    }

    @Test
    void remindsOfAQuestionToThePrimaryOnceTheIntervalHasPassedSinceItWasLastRaised() {
        Worker asking = launched().reported(new StatusReport(StatusState.NEEDS_DECISION, new StatusKey("lamp"), null, "Which lamp?"), LAUNCHED);
        Worker working = asking.reported(report(StatusState.WORKING, null), LAUNCHED.plusSeconds(60));
        Instant tenMinutesLater = LAUNCHED.plus(Duration.ofMinutes(10));

        assertThat(RULES.remindsOfTheQuestion(asking, LAUNCHED, tenMinutesLater.minusSeconds(1))).isFalse();
        assertThat(RULES.remindsOfTheQuestion(asking, LAUNCHED, tenMinutesLater)).isTrue();
        assertThat(RULES.remindsOfTheQuestion(asking, tenMinutesLater, tenMinutesLater.plusSeconds(60))).isFalse();
        assertThat(RULES.remindsOfTheQuestion(working, LAUNCHED, tenMinutesLater)).isFalse();
        assertThat(RULES.remindsOfTheQuestion(asking.retired(LAUNCHED.plusSeconds(1)), LAUNCHED, tenMinutesLater)).isFalse();
        assertThatThrownBy(() -> new SupervisionRules(Duration.ofMinutes(20), Duration.ofHours(4), Duration.ofHours(3), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void holdsAWorkerParkedOnItsLimitUntilItsNextPromptOrNormalStop() {
        UsageLimit limit = new UsageLimit("session limit", LAUNCHED.plus(Duration.ofHours(2)));
        Worker parked = launched().promptSubmitted(LAUNCHED).parkedOn(limit, LAUNCHED.plusSeconds(60));

        assertThat(health(parked, WindowCheck.OPEN, BOOT)).isEqualTo(new WorkerHealth.LimitParked(limit));
        assertThat(parked.promptSubmitted(LAUNCHED.plus(Duration.ofHours(2))).limitPark()).isNull();
        assertThat(parked.turnEnded(LAUNCHED.plus(Duration.ofHours(2))).limitPark()).isNull();
        assertThat(parked.sessionEnded(LAUNCHED.plus(Duration.ofHours(2))).limitPark()).isEqualTo(parked.limitPark());
    }

    @Test
    void holdsAWorkerWhoseContextIsFullUntilAPromptGetsThroughAndRaisesNoFurtherNotice() {
        Instant full = LAUNCHED.plus(Duration.ofHours(1));
        Worker filled = launched().promptSubmitted(LAUNCHED).contextFilled(full);

        assertThat(health(filled, WindowCheck.OPEN, BOOT)).isEqualTo(new WorkerHealth.ContextFull(full));
        assertThat(RULES.noticeDue(filled, new WorkerHealth.ContextFull(full), new Awaited(false, false), LAUNCHED.plus(Duration.ofHours(9)))).isEmpty();
        assertThat(health(filled.stopRequested(full.plusSeconds(60)), WindowCheck.WINDOW_CLOSED, BOOT)).isEqualTo(new WorkerHealth.Stopped(full.plusSeconds(60)));
        assertThat(filled.stopRequested(full.plusSeconds(60)).sessionEnded(full.plusSeconds(61)).hasAFullContext()).isTrue();
        assertThat(filled.promptSubmitted(full.plusSeconds(60)).hasAFullContext()).isFalse();
    }

    @Test
    void waitsUntilADeclaredPauseEndsAndThenNoticesItOnce() {
        Instant reported = LAUNCHED.plusSeconds(10);
        Worker paused = launched().started(null, null, LAUNCHED).reported(report(StatusState.PAUSED, LAUNCHED.plus(Duration.ofHours(1))), reported);
        Worker openEnded = launched().started(null, null, LAUNCHED).reported(report(StatusState.PAUSED, null), reported);

        assertThat(health(paused, WindowCheck.OPEN, BOOT, LAUNCHED.plus(Duration.ofMinutes(59)))).isEqualTo(new WorkerHealth.Waiting(LAUNCHED.plus(Duration.ofHours(1))));
        assertThat(noticeDue(paused, LAUNCHED.plus(Duration.ofHours(1)))).contains(new WorkerNotice(NoticeKind.WAIT_ENDED, reported));
        assertThat(noticeDue(openEnded, reported.plus(Duration.ofHours(4)).minusSeconds(1))).isEmpty();
        assertThat(noticeDue(openEnded, reported.plus(Duration.ofHours(4)))).contains(new WorkerNotice(NoticeKind.WAIT_ENDED, reported));
    }

    @Test
    void noticesAQuietWorkerOnceItHasWaitedTooLongUnlessItWaitsOnThePrimary() {
        Worker quiet = launched().started(null, null, LAUNCHED);
        Worker reportedWorking = quiet.reported(report(StatusState.WORKING, null), LAUNCHED.plus(Duration.ofMinutes(10)));

        assertThat(noticeDue(quiet, LAUNCHED.plus(Duration.ofMinutes(20)).minusSeconds(1))).isEmpty();
        assertThat(noticeDue(quiet, LAUNCHED.plus(Duration.ofMinutes(20)))).contains(new WorkerNotice(NoticeKind.STALE, LAUNCHED));
        assertThat(noticeDue(reportedWorking, LAUNCHED.plus(Duration.ofMinutes(25)))).isEmpty();
        assertThat(noticeDue(reportedWorking, LAUNCHED.plus(Duration.ofMinutes(30)))).contains(new WorkerNotice(NoticeKind.STALE, LAUNCHED.plus(Duration.ofMinutes(10))));
        for (StatusState waitingOnThePrimary : new StatusState[] {StatusState.DONE, StatusState.NEEDS_DECISION, StatusState.BLOCKED, StatusState.FAILED}) {
            Worker waiting = quiet.reported(report(waitingOnThePrimary, null), LAUNCHED);
            assertThat(noticeDue(waiting, LAUNCHED.plus(Duration.ofHours(5)))).as(waitingOnThePrimary.word()).isEmpty();
        }
        assertThat(RULES.noticeDue(quiet, RULES.healthOf(quiet, WindowCheck.OPEN, BOOT, LAUNCHED.plus(Duration.ofHours(1))), new Awaited(true, false), LAUNCHED.plus(Duration.ofHours(1))))
                .isEmpty();
        assertThat(RULES.noticeDue(quiet, RULES.healthOf(quiet, WindowCheck.OPEN, BOOT, LAUNCHED.plus(Duration.ofHours(1))), new Awaited(false, true), LAUNCHED.plus(Duration.ofHours(1))))
                .as("an idle worker whose pipeline runs").isEmpty();
    }

    @Test
    void noticesABusyWorkerOnceItPassesTheCeilingAndADeadOneAtOnce() {
        Worker busy = launched().promptSubmitted(LAUNCHED);

        assertThat(noticeDue(busy, LAUNCHED.plus(Duration.ofHours(3)).minusSeconds(1))).isEmpty();
        assertThat(noticeDue(busy, LAUNCHED.plus(Duration.ofHours(3)))).contains(new WorkerNotice(NoticeKind.BUSY_TOO_LONG, LAUNCHED));
        assertThat(RULES.noticeDue(busy, new WorkerHealth.Dead(DeathCause.WINDOW_CLOSED), Awaited.NOTHING, LAUNCHED)).contains(new WorkerNotice(NoticeKind.DIED, LAUNCHED));
    }

    private static Worker launched() {
        return Worker.launched(WorkerId.first(new TaskId("fit-lamp")), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 7, LAUNCHED, "@1", 8),
                new WorkerLaunch(LaunchKind.DISPATCH, LAUNCHED, BOOT));
    }

    private static StatusReport report(StatusState state, Instant until) {
        return new StatusReport(state, state == StatusState.RESOLVED ? new StatusKey("lamp") : null, until, "The lamp, " + state.word() + ".");
    }

    private static WorkerHealth health(Worker worker, WindowCheck window, BootId boot) {
        return health(worker, window, boot, LAUNCHED.plusSeconds(30));
    }

    private static WorkerHealth health(Worker worker, WindowCheck window, BootId boot, Instant now) {
        return RULES.healthOf(worker, window, boot, now);
    }

    private static Optional<WorkerNotice> noticeDue(Worker worker, Instant now) {
        return RULES.noticeDue(worker, RULES.healthOf(worker, WindowCheck.OPEN, BOOT, now), Awaited.NOTHING, now);
    }
}
