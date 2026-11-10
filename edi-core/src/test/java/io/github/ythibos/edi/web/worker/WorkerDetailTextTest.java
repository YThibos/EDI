package io.github.ythibos.edi.web.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.worker.WorkerDetail;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.BootId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.web.PresenterFixtures;

class WorkerDetailTextTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final Instant AT = PresenterFixtures.AT;
    private static final StatusKey COLOUR = new StatusKey("lamp-colour");

    private final WorkerDetailText text = new WorkerDetailText(PresenterFixtures.moments());

    @Test
    void showsAWorkingWorkersWholeLastStatusAndItsPipeline() {
        Worker working = launched().promptSubmitted(AT.plusSeconds(60))
                .reported(new StatusReport(StatusState.WORKING, null, null, "Fitting the lamp.\nThe bracket comes next."), AT.plusSeconds(120));

        assertThat(text.text(new WorkerDetail(working, List.of(), new PipelineRun("run-7", "running", "review", "abc1234", null, false)))).isEqualTo("""
                fit-lamp#1, implementation, on claude-opus-5-5 at high
                State: busy since 2026-10-15 09:31 UTC.
                Last status, 2026-10-15 09:32 UTC: working
                  Fitting the lamp.
                  The bracket comes next.
                Steers (0)
                Pipeline: run run-7 at its review step, running.
                """);
    }

    @Test
    void showsAnOpenQuestionInFullWithItsKeyAndTheSteersWithTheirAcknowledgement() {
        Worker asking = launched().promptSubmitted(AT.plusSeconds(60))
                .reported(new StatusReport(StatusState.NEEDS_DECISION, COLOUR, null, "Brass or copper?\nThe brass one is heavier."), AT.plusSeconds(120));
        InboxMessage acknowledged = new InboxMessage(LAMP, 1, "Use the brass bracket.", null, AT.plusSeconds(180), AT.plusSeconds(200),
                AT.plusSeconds(240), null);
        InboxMessage waiting = InboxMessage.sent(LAMP, 2, "Copper, polished.\nKeep the old screws.", COLOUR, AT.plusSeconds(300));

        assertThat(text.text(new WorkerDetail(asking, List.of(acknowledged, waiting), null))).isEqualTo("""
                fit-lamp#1, implementation, on claude-opus-5-5 at high
                State: busy since 2026-10-15 09:31 UTC.
                Last status, 2026-10-15 09:32 UTC: needs-decision (lamp-colour)
                  Brass or copper?
                  The brass one is heavier.
                Open question lamp-colour: it waits on the primary since 2026-10-15 09:32 UTC; edi steer fit-lamp --resolves lamp-colour answers it.
                Steers (2)
                  1  sent 2026-10-15 09:33 UTC, acknowledged 2026-10-15 09:34 UTC
                     Use the brass bracket.
                  2  sent 2026-10-15 09:35 UTC, not yet received, resolving lamp-colour
                     Copper, polished.
                     Keep the old screws.
                Pipeline: no run yet.
                """);
    }

    @Test
    void showsAFinishedWorkersDoneAndItsCheck() {
        Worker finished = launched().promptSubmitted(AT.plusSeconds(60))
                .reported(new StatusReport(StatusState.DONE, null, null, "The lamp is fitted."), AT.plusSeconds(120))
                .turnEnded(AT.plusSeconds(150));

        assertThat(text.text(new WorkerDetail(finished, List.of(), new PipelineRun("run-8", "passed", null, "abc1234", null, false)))).isEqualTo("""
                fit-lamp#1, implementation, on claude-opus-5-5 at high
                State: idle since 2026-10-15 09:32 UTC.
                Done check: accepted.
                Last status, 2026-10-15 09:32 UTC: done
                  The lamp is fitted.
                Steers (0)
                Pipeline: run run-8, passed.
                """);
    }

    private static Worker launched() {
        return Worker.launched(WorkerId.first(LAMP), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 1L, AT, "@1", 2L),
                new WorkerLaunch(LaunchKind.DISPATCH, AT, new BootId("boot"), LoginName.DEFAULT));
    }
}
