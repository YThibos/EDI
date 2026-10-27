package io.github.ythibos.edi.application.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.primary.BackgroundJob;
import io.github.ythibos.edi.domain.primary.PrimarySession;
import io.github.ythibos.edi.domain.primary.SessionProcess;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.worker.LimitStop;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

class PrimaryHooksTest {

    private static final List<BackgroundJob> A_LIVE_WAIT = List.of(new BackgroundJob("shell", "running", "edi wait"));

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
    }

    @Test
    void givesANewSessionTheRoleOnlyWhenNoLiveSessionHoldsIt() throws IOException {
        Process running = processes.register(new ProcessBuilder("sleep", "30").start());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        home.primaryHooks().sessionStarted(first, running.pid(), true);
        home.primaryHooks().sessionStarted(second, null, true);

        assertThat(home.primarySessions().standingOf(first)).isEqualTo(Standing.PRIMARY);
        assertThat(home.primarySessions().standingOf(second)).isEqualTo(Standing.STANDBY);

        running.destroyForcibly();
        TestProcesses.awaitExit(running.toHandle());
        UUID third = UUID.randomUUID();
        home.primaryHooks().sessionStarted(third, null, true);

        assertThat(home.primarySessions().standingOf(third)).isEqualTo(Standing.PRIMARY);
        assertThat(home.primarySessions().standingOf(first)).isEqualTo(Standing.STANDBY);
    }

    @Test
    void keepsTheRoleOfALiveSessionAcrossACoreRestartUntilAnotherProcessReusesItsId() throws IOException {
        Process claude = processes.register(new ProcessBuilder("sleep", "30").start());
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, claude.pid(), true);
        TestHome restarted = home.restarted();

        assertThat(restarted.primarySessions().standingOf(primary)).isEqualTo(Standing.PRIMARY);

        recordTheStartOneTickLater(restarted, primary);

        assertThat(restarted.primarySessions().standingOf(primary)).isEqualTo(Standing.UNCLAIMED);
        claude.destroyForcibly();
        assertThat(TestProcesses.awaitExit(claude.toHandle())).isTrue();
    }

    @Test
    void keepsTheRoleWhenTheSameSessionStartsAgainAndFreesItWhenTheSessionEnds() {
        UUID primary = UUID.randomUUID();
        UUID standby = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, true);
        home.primaryHooks().sessionStarted(standby, null, true);
        home.primaryHooks().sessionStarted(primary, null, true);

        assertThat(home.primarySessions().standingOf(primary)).isEqualTo(Standing.PRIMARY);

        home.primaryHooks().sessionEnded(primary);

        assertThat(home.primarySessions().standingOf(standby)).isEqualTo(Standing.UNCLAIMED);
    }

    @Test
    void endsATurnWithWorkInFlightOnlyWhileAnEdiWaitIsLive() throws IOException {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID primary = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, true);
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");

        HookAnswer blind = home.primaryHooks().stopping(primary, List.of());
        HookAnswer waiting = home.primaryHooks().stopping(primary, A_LIVE_WAIT);

        assertThat(blind.verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
        assertThat(blind.reason()).isEqualTo("Work is in flight and no edi wait is live: start edi wait in the background, then stop.");
        assertThat(waiting).isEqualTo(HookAnswer.PROCEED);
    }

    @Test
    void neverHoldsAStandbySessionsStopOrBlocksOneTurnMoreThanThreeTimes() {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID primary = UUID.randomUUID();
        UUID standby = UUID.randomUUID();
        home.primaryHooks().sessionStarted(primary, null, true);
        home.primaryHooks().sessionStarted(standby, null, true);
        home.primaryHooks().promptSubmitted(standby, "<task-notification>done</task-notification>");
        home.primaryHooks().promptSubmitted(primary, "How is the lamp?");

        assertThat(home.primaryHooks().stopping(standby, List.of())).isEqualTo(HookAnswer.PROCEED);
        for (int block = 0; block < 3; block++) {
            assertThat(home.primaryHooks().stopping(primary, List.of()).verdict()).isEqualTo(HookAnswer.Verdict.BLOCK_THE_STOP);
        }
        assertThat(home.primaryHooks().stopping(primary, List.of())).isEqualTo(HookAnswer.PROCEED);
    }

    @Test
    void tellsTheNextSessionThatThePreviousPrimaryStoppedOnItsUsageLimit() throws IOException {
        Instant reset = home.clock().now().plus(Duration.ofHours(2));
        home.runtime().transcriptEndsOn(new LimitStop(home.clock().now(), new UsageLimit("session limit", reset)));
        UUID stopped = UUID.randomUUID();
        home.primaryHooks().sessionStarted(stopped, null, true);
        home.primaryHooks().stopFailed(stopped, "/transcripts/stopped.jsonl");
        home.primaryHooks().sessionEnded(stopped);

        home.clock().advance(Duration.ofMinutes(1));
        UUID next = UUID.randomUUID();
        home.primaryHooks().sessionStarted(next, null, true);
        home.clock().advance(Duration.ofMinutes(1));
        UUID later = UUID.randomUUID();
        home.primaryHooks().sessionStarted(later, null, true);

        assertThat(home.sessionStartDigest().digestFor(next).previousLimitStop().limit()).isEqualTo(new UsageLimit("session limit", reset));
        assertThat(home.sessionStartDigest().digestFor(later).previousLimitStop()).isNull();
    }

    private static void recordTheStartOneTickLater(TestHome home, UUID session) {
        PrimarySession recorded = home.primarySessionStore().get(session).orElseThrow();
        SessionProcess reused = new SessionProcess(recorded.process().pid(), recorded.process().startTick() + 1);
        home.primarySessionStore().save(recorded.restarted(reused));
    }
}
