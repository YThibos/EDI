package io.github.ythibos.edi.application.follow;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.EventRecorded;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;

class EventFollowersTest {

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

    private static long latestSequenceIn(TestHome home) {
        return home.events().after(0, Integer.MAX_VALUE).getLast().sequence();
    }
}
