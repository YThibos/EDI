package io.github.ythibos.edi.application.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.WorkedTime;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkEvents;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class TaskDetailQueryTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void storeAShip() throws IOException {
        home = TestHome.in(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fix-lamps", "harness", "HRN-40")));
    }

    @Test
    void measuresTheActiveTimeOfATaskInFlightUpToNow() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/HRN-40")
                .reported(StatusState.WORKING, null, "HRN-40 set-up done")
                .after(Duration.ofMinutes(30))
                .reported(StatusState.NEEDS_DECISION, "lamp-colour", "Which colour?")
                .after(Duration.ofMinutes(30))
                .reported(StatusState.WORKING, null, "HRN-40 painting")
                .after(Duration.ofMinutes(15));

        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(45), Duration.ofMinutes(75)));
    }

    @Test
    void measuresTheActiveTimeOfAClosedTaskUpToItsClosing() {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-41").dispatched()
                .cleanedUp(TestHome.START.plus(Duration.ofMinutes(20)))));
        WorkEvents.of(home, "fit-horn").dispatchedTo(1, "feature/HRN-41")
                .reported(StatusState.WORKING, null, "HRN-41 set-up done")
                .after(Duration.ofHours(3));

        assertThat(home.taskDetail().detail(TaskReference.parse("fit-horn")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(20), Duration.ofMinutes(20)));
    }

    @Test
    void measuresNothingForATaskNotYetDispatched() {
        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked()).isNull();
    }
}
