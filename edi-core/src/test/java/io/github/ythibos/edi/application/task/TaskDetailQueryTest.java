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
    void leavesOutTheTimeAWorkerWaitsOnItsUsageLimit() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/HRN-40")
                .reported(StatusState.WORKING, null, "HRN-40 set-up done")
                .after(Duration.ofMinutes(20))
                .reachedItsUsageLimitUntil(Duration.ofHours(3).plusMinutes(40))
                .after(Duration.ofHours(3).plusMinutes(40))
                .relaunched()
                .after(Duration.ofMinutes(30))
                .reported(StatusState.DONE, null, "HRN-40 delivered");

        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(50), Duration.ofHours(4).plusMinutes(30)));
    }

    @Test
    void leavesOutTheTimeAWorkerStandsStoppedOnAnError() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/HRN-40")
                .reported(StatusState.WORKING, null, "HRN-40 set-up done")
                .after(Duration.ofMinutes(20))
                .stoppedOnAnError()
                .after(Duration.ofHours(1));

        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(20), Duration.ofMinutes(80)));
    }

    @Test
    void countsTheWorkAWorkerResumesOnASteerAfterAnError() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/HRN-40")
                .reported(StatusState.WORKING, null, "HRN-40 set-up done")
                .after(Duration.ofMinutes(20))
                .stoppedOnAnError()
                .receivedASteer()
                .after(Duration.ofMinutes(40))
                .reported(StatusState.WORKING, null, "HRN-40 painting")
                .after(Duration.ofMinutes(30))
                .reported(StatusState.DONE, null, "HRN-40 delivered");

        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(90), Duration.ofMinutes(90)));
    }

    @Test
    void endsTheStretchAMessageStartsWhenTheWorkersTurnEnds() {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-41").dispatched()
                .cleanedUp(TestHome.START.plus(Duration.ofHours(27)))));
        WorkEvents.of(home, "fit-horn").dispatchedTo(1, "feature/HRN-41")
                .reported(StatusState.WORKING, null, "HRN-41 set-up done")
                .after(Duration.ofHours(2))
                .reported(StatusState.DONE, null, "HRN-41 delivered")
                .after(Duration.ofHours(1))
                .receivedASteer()
                .after(Duration.ofMinutes(5))
                .endedItsTurn()
                .after(Duration.ofHours(24).minusMinutes(5))
                .cleanedUp();

        assertThat(home.taskDetail().detail(TaskReference.parse("fit-horn")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(125), Duration.ofHours(27)));
    }

    @Test
    void endsTheStretchWhenATurnEndedThatEdiNoticedLate() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/HRN-40")
                .reported(StatusState.WORKING, null, "HRN-40 set-up done")
                .after(Duration.ofHours(2))
                .endedItsTurnUnnoticedFor(Duration.ofMinutes(90))
                .receivedASteer();

        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(30), Duration.ofHours(2)));
    }

    @Test
    void endsTheStretchWhenAUsageLimitStoppedTheWorkerThatEdiNoticedLate() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/HRN-40")
                .reported(StatusState.WORKING, null, "HRN-40 set-up done")
                .after(Duration.ofHours(2))
                .reachedItsUsageLimitUnnoticedFor(Duration.ofMinutes(90));

        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked())
                .isEqualTo(new WorkedTime(Duration.ofMinutes(30), Duration.ofHours(2)));
    }

    @Test
    void measuresNothingForATaskNotYetDispatched() {
        assertThat(home.taskDetail().detail(TaskReference.parse("fix-lamps")).worked()).isNull();
    }
}
