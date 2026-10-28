package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome.Launched;
import io.github.ythibos.edi.application.port.WorkerRuntime.Launch;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class RelaunchWorkerTest {

    private static final TaskReference LAMP = new TaskReference.ById(new TaskId("fit-lamp"));

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker lamp;

    @BeforeEach
    void closeTheWindowOfAWorker() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        home.runtime().closeEveryWindow();
    }

    @Test
    void resumesTheRecordedSessionWhenItsTranscriptIsKept() {
        Launched relaunched = home.relaunchWorker().relaunch(LAMP, Actor.PRIMARY);

        Launch resumed = home.runtime().lastLaunch();
        assertThat(resumed.kind()).isEqualTo(LaunchKind.RESUME);
        assertThat(resumed.session()).isEqualTo(lamp.session());
        assertThat(relaunched.unresumableSession()).isNull();
    }

    @Test
    void startsAfreshWithAHandoverWhenTheRecordedSessionLeftNoTranscript() {
        home.runtime().leaveNoTranscriptOf(lamp.session());

        Launched relaunched = home.relaunchWorker().relaunch(LAMP, Actor.PRIMARY);

        Launch fresh = home.runtime().lastLaunch();
        Path handover = home.home().taskDirectory(lamp.id().task()).resolve("handover.md");
        String because = "its window closed and session " + lamp.session() + " left no transcript to resume";
        assertThat(fresh.kind()).isEqualTo(LaunchKind.FRESH);
        assertThat(fresh.session()).isNotEqualTo(lamp.session());
        assertThat(fresh.firstPrompt()).isEqualTo("⁣EDI_OP: fresh start after " + because + ": read the handover " + handover
                + " and carry on from where it leaves off");
        assertThat(handover).content().contains("fit-lamp#2 takes over from fit-lamp#1 in the same isolated copy, because " + because + ".");
        assertThat(relaunched.unresumableSession()).isEqualTo(lamp.session());
        assertThat(relaunched.worker().launch().kind()).isEqualTo(LaunchKind.FRESH);
    }
}
