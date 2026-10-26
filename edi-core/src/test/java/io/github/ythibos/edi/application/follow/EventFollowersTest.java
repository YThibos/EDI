package io.github.ythibos.edi.application.follow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.EventRecorded;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;
import io.github.ythibos.edi.testing.WorkEvents;
import io.github.ythibos.edi.testing.WorkerFixtures;

class EventFollowersTest {

    private static final int FULL_BATCH = 100;

    @TempDir
    private Path directory;

    @Test
    void stopsOnlyOnceTheRunsInFlightHaveFollowedTheirEvents() throws Exception {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY).writing("config/machine.yaml", "notify:\n  windowsToast: true\n");
        home.changePresence().quiet();
        home.addTask().add(Intakes.ship("fit-lamp", "Fit the brass lamp"), Actor.PRIMARY);
        home.raiseDecision().raise(TaskReference.parse("fit-lamp"), Cards.card("Which lamp?"), Actor.PRIMARY);
        EventFollowers followers = home.eventFollowers();

        followers.eventRecorded(new EventRecorded(latestSequenceIn(home)));
        followers.close();

        assertThat(home.windowsSide().toasts()).containsExactly("Decision D1 waits on you | Which lamp?");
    }

    @Test
    void followsEveryEventOnceBeyondAFullBatchAndInTheNextRun() throws IOException {
        TestHome home = TrackerFixtures.laptopHome(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fix-lamps", "service", "SVC-40")));
        WorkEvents lamps = WorkEvents.of(home, "fix-lamps");
        EventFollowers followers = home.eventFollowers();

        home.inOneTransaction(() -> recordCleanUps(lamps, FULL_BATCH + 1));
        followers.followTheLog();
        List<String> afterTheFirstRun = Files.readAllLines(home.home().fleetLedger());
        lamps.cleanedUp();
        followers.followTheLog();

        assertThat(afterTheFirstRun).hasSize(FULL_BATCH + 1);
        assertThat(Files.readAllLines(home.home().fleetLedger())).hasSize(FULL_BATCH + 2);
    }

    private static void recordCleanUps(WorkEvents work, int count) {
        for (int recorded = 0; recorded < count; recorded++) {
            work.cleanedUp();
        }
    }

    private static long latestSequenceIn(TestHome home) {
        return home.events().after(0, Integer.MAX_VALUE).getLast().sequence();
    }
}
