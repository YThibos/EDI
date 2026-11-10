package io.github.ythibos.edi.web.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.adapter.store.RecoveryCostStore.CountedRecovery;
import io.github.ythibos.edi.application.supervision.OutageList;
import io.github.ythibos.edi.application.supervision.OutageList.UnpushedWork;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
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
        Task task = Task.queued(new TaskId("tune-horn"), new Intake(TaskKind.SHIP, "Tune the horn", List.of(new ProjectName("harness")), null, null, List.of(), null), "", AT)
                .held(entry.hold());
        Worker worker = Worker.launched(entry.worker(), SESSION, CategoryId.IMPLEMENTATION, new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi", 7, AT, "@1", 8), new WorkerLaunch(LaunchKind.DISPATCH, AT, null, LoginName.DEFAULT))
                .reported(new StatusReport(StatusState.WORKING, null, null, "Tests pass."), AT);
        OutageList list = new OutageList(List.of(new OutageList.Entry(entry, task, worker, new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH),
                null, 1, new UnpushedWork(true, 2, true), Path.of("/home/edi/worktrees/harness/tune-horn"))),
                List.of(new CountedRecovery(LaunchKind.RESUME, new TokenUsage(1, 2, 3, 4))));

        assertThat(new OutageListText().text(list)).isEqualTo("""
                Outage hold list (1)
                  2   tune-horn  harness  lost when the machine restarted
                       outcome so far: working: Tests pass.
                       waits on: 1 unread messages in its inbox
                       ran on claude-sonnet-5-5 at high; its category gives claude-opus-5-5 at high now
                       unpushed work: 2 commits on no remote and uncommitted changes
                       resume: session 0f0e0d0c-0b0a-4908-8706-050403020100 in /home/edi/worktrees/harness/tune-horn
                Answer with one number and resume or fresh, such as edi recover 2 resume or edi recover 4 fresh --effort medium; you act on one number at a time.
                Recovery so far: 1 relaunches counted (1 resumed, 0 fresh), 10 tokens (input 1, cache read 2, cache write 3, output 4).
                """);
    }
}
