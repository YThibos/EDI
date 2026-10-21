package io.github.ythibos.edi.application.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.PresenceMode;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import tools.jackson.databind.json.JsonMapper;

class PresenceFollowingTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHomeWithToasts() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n")
                .writing("config/machine.yaml", "notify:\n  windowsToast: true\n");
    }

    @Test
    void capsTheWorkersWhileAwayAtTheNumberRunningWhenTheCaptainLeft() {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        typed("/afk Carry on with the lamp.");

        Presence away = home.changePresence().away("Carry on with the lamp.");

        assertThat(away.mode()).isEqualTo(PresenceMode.AWAY);
        assertThat(away.mandate().workerCap()).isOne();
        assertThat(home.awayLimit().hasRoomForAnotherWorker()).isFalse();
    }

    @Test
    void leavesWorkersWhoseWindowIsGoneOutOfTheCapWhileAway() {
        WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        Worker dead = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-door", "harness", "HRN-6"), CategoryId.IMPLEMENTATION);
        home.runtime().close(dead.window());
        typed("/afk Carry on with the lamp.");

        Presence away = home.changePresence().away("Carry on with the lamp.");

        assertThat(away.mandate().workerCap()).isOne();
        assertThat(home.awayLimit().hasRoomForAnotherWorker()).isFalse();
    }

    @Test
    void notifiesTheCaptainOnceOfEachDecisionAndFailureSinceTheQuietBegan() throws IOException {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        home.storing(List.of(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null)));
        home.recordStatus().report(worker.id(), StatusState.BLOCKED, null, null, "The forge login expired.");
        home.clock().advance(Duration.ofSeconds(1));
        home.changePresence().quiet();
        home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Fitting the lamp.");
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");
        home.raiseDecision().raise(TaskReference.parse("choose-lamp"), Cards.card("Which lamp?"), Actor.PRIMARY);

        home.eventFollowers().followTheLog();
        home.eventFollowers().followTheLog();

        assertThat(home.windowsSide().toasts()).containsExactly("Work on fit-lamp failed | The lamp broke.", "Decision D1 waits on you | Which lamp?");
    }

    @Test
    void notifiesNobodyWhileTheCaptainIsPresent() throws IOException {
        home.storing(List.of(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null)));
        home.raiseDecision().raise(TaskReference.parse("choose-lamp"), Cards.card("Which lamp?"), Actor.PRIMARY);

        home.eventFollowers().followTheLog();

        assertThat(home.windowsSide().toasts()).isEmpty();
    }

    @Test
    void briefsTheReturningCaptainOnWhatWaitsFailedAndRuns() {
        Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        UUID session = typed("Fit the lamp.");
        home.changePresence().quiet();
        home.clock().advance(Duration.ofMinutes(5));
        home.recordStatus().report(worker.id(), StatusState.FAILED, null, null, "The lamp broke.");

        ReturnBrief brief = home.primaryHooks().promptSubmitted(session, "Back.").orElseThrow();
        boolean briefedTwice = home.primaryHooks().promptSubmitted(session, "And again.").isPresent();

        assertThat(brief.left().mode()).isEqualTo(PresenceMode.QUIET);
        assertThat(brief.setbacks()).extracting(Setback::title, Setback::detail).containsExactly(tuple("Work on fit-lamp", "The lamp broke."));
        assertThat(brief.running()).extracting(ongoing -> ongoing.task().id().value()).containsExactly("fit-lamp");
        assertThat(briefedTwice).isFalse();
    }

    @Test
    void briefsTheCaptainAtTheirNextMessageAfterTheConsoleEndedTheirAbsence() {
        UUID session = typed("/afk Carry on with the lamp.");
        Presence away = home.changePresence().away("Carry on with the lamp.");
        home.clock().advance(Duration.ofMinutes(5));
        home.changePresence().demo();
        home.clock().advance(Duration.ofMinutes(5));
        home.changePresence().present();

        ReturnBrief brief = home.primaryHooks().promptSubmitted(session, "Back.").orElseThrow();
        boolean briefedTwice = home.primaryHooks().promptSubmitted(session, "And again.").isPresent();

        assertThat(brief.left()).isEqualTo(away);
        assertThat(briefedTwice).isFalse();
    }

    @Test
    void ignoresEventsFromBeforeTheQuietBegan() throws IOException {
        LoggedEvent earlier = new LoggedEvent(1, TestHome.START, Actor.CORE, "DecisionRaised",
                WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3").id(), new JsonMapper().createObjectNode());
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        home.clock().advance(Duration.ofMinutes(1));
        home.changePresence().quiet();

        home.notifyTheCaptain().follow(earlier);

        assertThat(home.windowsSide().toasts()).isEmpty();
    }

    private UUID typed(String prompt) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, true);
        home.primaryHooks().promptSubmitted(session, prompt);
        return session;
    }
}
