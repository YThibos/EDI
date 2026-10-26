package io.github.ythibos.edi.application.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;
import io.github.ythibos.edi.testing.WorkEvents;
import io.github.ythibos.edi.testing.WorkerFixtures;

class EmitFleetLedgerTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void storeAShip() throws IOException {
        home = TrackerFixtures.laptopHome(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fix-lamps", "service", "SVC-40")));
    }

    @Test
    void recordsATasksLifeForTheTimeBookingToolInTheOldHarnesssContract() throws IOException {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40")
                .reported(StatusState.WORKING, null, "SVC-40 set-up done")
                .reported(StatusState.DONE, null, "https://forge.example.org/team/service/merge_requests/7")
                .deliveredAsADraft()
                .dispatchedTo(2, "feature/SVC-40")
                .merged()
                .cleanedUp();

        home.eventFollowers().followTheLog();

        long at = TestHome.START.getEpochSecond();
        assertThat(Files.readAllLines(home.home().fleetLedger())).containsExactly(
                "{\"v\":1,\"ts\":" + at + ",\"event\":\"task.dispatched\",\"task\":\"fix-lamps\",\"kind\":\"ship\",\"project\":\"service\","
                        + "\"harness\":\"claude\",\"model\":\"claude-sonnet-5-5\",\"key\":\"SVC-40\"}",
                "{\"v\":1,\"ts\":" + at + ",\"event\":\"task.status\",\"task\":\"fix-lamps\",\"state\":\"working\",\"key\":null,\"text\":\"SVC-40 set-up done\"}",
                "{\"v\":1,\"ts\":" + at + ",\"event\":\"task.status\",\"task\":\"fix-lamps\",\"state\":\"done\",\"key\":null,"
                        + "\"text\":\"https://forge.example.org/team/service/merge_requests/7\"}",
                "{\"v\":1,\"ts\":" + at + ",\"event\":\"task.pr_ready\",\"task\":\"fix-lamps\",\"pr\":\"" + WorkEvents.DRAFT.url() + "\"}",
                "{\"v\":1,\"ts\":" + at + ",\"event\":\"task.merged\",\"task\":\"fix-lamps\",\"via\":\"pr\",\"pr\":\"" + WorkEvents.DRAFT.url() + "\"}",
                "{\"v\":1,\"ts\":" + at + ",\"event\":\"task.worked\",\"task\":\"fix-lamps\",\"key\":\"SVC-40\",\"active_minutes\":0,"
                        + "\"wall_clock_minutes\":0}",
                "{\"v\":1,\"ts\":" + at + ",\"event\":\"task.cleaned_up\",\"task\":\"fix-lamps\"}");
    }

    @Test
    void recordsTheActiveMinutesAtCleanupWithoutTheTimeSpentWaiting() throws IOException {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40")
                .reported(StatusState.WORKING, null, "SVC-40 set-up done")
                .after(Duration.ofMinutes(30))
                .reported(StatusState.NEEDS_DECISION, "lamp-colour", "Which colour?")
                .after(Duration.ofMinutes(30))
                .reported(StatusState.WORKING, null, "SVC-40 painting")
                .after(Duration.ofMinutes(15))
                .reported(StatusState.DONE, null, "https://forge.example.org/team/service/merge_requests/7")
                .after(Duration.ofMinutes(20))
                .cleanedUp();

        home.eventFollowers().followTheLog();

        assertThat(Files.readAllLines(home.home().fleetLedger())).contains(
                "{\"v\":1,\"ts\":" + home.clock().now().getEpochSecond() + ",\"event\":\"task.worked\",\"task\":\"fix-lamps\",\"key\":\"SVC-40\","
                        + "\"active_minutes\":45,\"wall_clock_minutes\":95}");
    }

    @Test
    void recordsEachEventOnceHoweverOftenItsFollowersRun() throws IOException {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40");
        home.eventFollowers().followTheLog();
        home.eventFollowers().followTheLog();
        WorkEvents.of(home, "fix-lamps").cleanedUp();
        home.eventFollowers().followTheLog();

        assertThat(Files.readAllLines(home.home().fleetLedger())).hasSize(3);
    }

    @Test
    void writesNothingOnAMachineWhoseProfileKeepsNoLedger() throws IOException {
        home.on(MachineFixture.DESKTOP);
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40").cleanedUp();

        home.eventFollowers().followTheLog();

        assertThat(home.home().fleetLedger()).doesNotExist();
    }
}
