package io.github.ythibos.edi.web.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.adapter.store.RecoveryCostStore.CountedRecovery;
import io.github.ythibos.edi.application.supervision.OutageList;
import io.github.ythibos.edi.application.supervision.OutageList.UnpushedWork;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.Counted;
import io.github.ythibos.edi.domain.landing.CommitsOnTheForge.NotVerified;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Hold;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;

class OutageListTextTest {

    private static final Instant AT = Instant.parse("2026-10-16T09:00:00Z");
    private static final UUID SESSION = UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100");

    @Test
    void numbersEachLostTaskWithWhatTheCaptainNeedsToChoose() {
        OutageEntry entry = new OutageEntry(2, new TaskId("tune-horn"), WorkerId.first(new TaskId("tune-horn")), DeathCause.MACHINE_RESTARTED, AT);
        Task task = Task.queued(new TaskId("tune-horn"), new Intake(TaskKind.SHIP, "Tune the horn", List.of(new ProjectName("harness")), null, null, List.of(), null, null), "", AT)
                .held(entry.hold());
        Worker worker = Worker.launched(entry.worker(), SESSION, CategoryId.IMPLEMENTATION, new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi", 7, AT, "@1", 8), new WorkerLaunch(LaunchKind.DISPATCH, AT, null, LoginName.DEFAULT))
                .reported(new StatusReport(StatusState.WORKING, null, null, "Tests pass."), AT);
        OutageList list = new OutageList(List.of(new OutageList.Entry(entry, task, worker, new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH),
                1, new UnpushedWork(new Counted("origin", 2, Optional.of(Duration.ofMinutes(5))), true), Path.of("/home/edi/worktrees/harness/tune-horn"))),
                List.of(new CountedRecovery(LaunchKind.RESUME, new TokenUsage(1, 2, 3, 4))));

        assertThat(new OutageListText().text(list)).isEqualTo("""
                Outage hold list (1)
                  2   tune-horn  harness  lost when the machine restarted
                       outcome so far: working: Tests pass.
                       waits on: 1 unread messages in its inbox
                       ran on claude-sonnet-5-5 at high; its category gives claude-opus-5-5 at high now
                       unpushed work: 2 commits not on origin and uncommitted changes
                       resume: session 0f0e0d0c-0b0a-4908-8706-050403020100 in /home/edi/worktrees/harness/tune-horn
                Answer with one number and resume or fresh, such as edi recover 2 resume or edi recover 4 fresh --effort medium; you act on one number at a time.
                Recovery so far: 1 relaunches counted (1 resumed, 0 fresh), 10 tokens (input 1, cache read 2, cache write 3, output 4).
                """);
    }

    @Test
    void namesEveryDecisionALostTaskStillWaitsOn() {
        OutageEntry entry = new OutageEntry(3, new TaskId("tune-horn"), WorkerId.first(new TaskId("tune-horn")), DeathCause.MACHINE_RESTARTED, AT);
        Task task = Task.queued(new TaskId("tune-horn"), new Intake(TaskKind.SHIP, "Tune the horn", List.of(new ProjectName("harness")), null, null, List.of(), null, null), "", AT)
                .held(entry.hold())
                .held(new Hold.OpenDecision(new DecisionNumber(10), "Which lens?", AT))
                .held(new Hold.OpenDecision(new DecisionNumber(11), "Which paint?", AT))
                .decisionAnswered(new DecisionNumber(11));
        Worker worker = Worker.launched(entry.worker(), SESSION, CategoryId.IMPLEMENTATION, new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi", 7, AT, "@1", 8), new WorkerLaunch(LaunchKind.DISPATCH, AT, null, LoginName.DEFAULT));
        OutageList list = new OutageList(List.of(new OutageList.Entry(entry, task, worker, new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                0, new UnpushedWork(new Counted("origin", 0, Optional.of(Duration.ofMinutes(5))), false), Path.of("/home/edi/worktrees/harness/tune-horn"))),
                List.of());

        assertThat(new OutageListText().text(list)).contains("       waits on: the captain's answer to D10\n");
    }

    @Test
    void saysNoneOnlyWhenTheForgeHoldsEveryCommit() {
        assertThat(unpushedLine(new UnpushedWork(new Counted("origin", 0, Optional.of(Duration.ofMinutes(5))), false))).isEqualTo("none");
    }

    @Test
    void namesTheRemoteTheCommitsAreMissingFrom() {
        assertThat(unpushedLine(new UnpushedWork(new Counted("origin", 1, Optional.of(Duration.ofMinutes(5))), false))).isEqualTo("1 commit not on origin");
    }

    @Test
    void saysHowOldTheCopysKnowledgeIsWhenItMayBeOutOfDate() {
        assertThat(unpushedLine(new UnpushedWork(new Counted("origin", 0, Optional.of(Duration.ofDays(3))), false)))
                .isEqualTo("none (the copy last fetched from the forge 3 days ago)");
        assertThat(unpushedLine(new UnpushedWork(new Counted("origin", 2, Optional.empty()), false)))
                .isEqualTo("2 commits not on origin (the copy records no fetch from the forge)");
    }

    @Test
    void neverSaysNoneForCommitsItCouldNotCheckAgainstTheForge() {
        assertThat(unpushedLine(new UnpushedWork(new NotVerified("no remote of its copy pushes to https://github.com/example-owner/harness"), false)))
                .isEqualTo("not verified, because no remote of its copy pushes to https://github.com/example-owner/harness");
        assertThat(unpushedLine(new UnpushedWork(new NotVerified("its remotes cannot be told apart"), true)))
                .isEqualTo("uncommitted changes, and commits not verified, because its remotes cannot be told apart");
    }

    private static String unpushedLine(UnpushedWork unpushed) {
        OutageEntry entry = new OutageEntry(1, new TaskId("tune-horn"), WorkerId.first(new TaskId("tune-horn")), DeathCause.MACHINE_RESTARTED, AT);
        Task task = Task.queued(new TaskId("tune-horn"), new Intake(TaskKind.SHIP, "Tune the horn", List.of(new ProjectName("harness")), null, null, List.of(), null, null), "", AT)
                .held(entry.hold());
        Worker worker = Worker.launched(entry.worker(), SESSION, CategoryId.IMPLEMENTATION, new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi", 7, AT, "@1", 8), new WorkerLaunch(LaunchKind.DISPATCH, AT, null, LoginName.DEFAULT));
        OutageList list = new OutageList(List.of(new OutageList.Entry(entry, task, worker, worker.route(), 0, unpushed, Path.of("/home/edi/worktrees/harness/tune-horn"))),
                List.of());
        return new OutageListText().text(list).lines().filter(line -> line.contains("unpushed work: ")).findFirst().orElseThrow()
                .substring("       unpushed work: ".length());
    }
}
