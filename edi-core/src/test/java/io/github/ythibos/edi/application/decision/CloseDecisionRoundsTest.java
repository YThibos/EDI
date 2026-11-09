package io.github.ythibos.edi.application.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionRound;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRoundClosed;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class CloseDecisionRoundsTest {

    private static final TaskId FIT_LAMP = new TaskId("fit-lamp");
    private static final TaskId PAINT_TOWER = new TaskId("paint-tower");
    private static final TaskId OIL_HINGES = new TaskId("oil-hinges");
    private static final TaskId SWEEP_STAIRS = new TaskId("sweep-stairs");
    private static final TaskId PLAN_GARDEN = new TaskId("plan-garden");
    private static final TaskId CHOOSE_BULB = new TaskId("choose-bulb");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void putFourShipsInFlight() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        home.storing(List.of(
                WorkerFixtures.queuedShip(FIT_LAMP.value(), "harness", "HRN-1"),
                WorkerFixtures.queuedShip(PAINT_TOWER.value(), "harness", "HRN-2"),
                WorkerFixtures.queuedShip(OIL_HINGES.value(), "harness", "HRN-3"),
                WorkerFixtures.queuedShip(SWEEP_STAIRS.value(), "harness", "HRN-4"),
                WorkerFixtures.queuedShip(PLAN_GARDEN.value(), "harness", "HRN-5"),
                WorkerFixtures.queued(CHOOSE_BULB.value(), TaskKind.CAPTAIN, "harness", null)));
        List.of(FIT_LAMP, PAINT_TOWER, OIL_HINGES, SWEEP_STAIRS).forEach(home::putInFlight);
    }

    @Test
    void threeDecisionsRaisedWithinTheWindowGoOutAsOneRoundAndALaterOneAsARoundOfItsOwn() {
        Instant opened = home.clock().now();
        DecisionNumber lamp = raise(FIT_LAMP, "Which lamp?");
        home.clock().advance(Duration.ofMinutes(1));
        DecisionNumber paint = raise(PAINT_TOWER, "Which paint?");
        home.clock().advance(Duration.ofMinutes(3));
        DecisionNumber oil = raise(OIL_HINGES, "Which oil?");
        home.clock().advance(Duration.ofSeconds(59));
        home.closeDecisionRounds().closeTheDueRound();

        assertThat(roundsClosed()).isEmpty();

        home.clock().advance(Duration.ofSeconds(1));
        home.closeDecisionRounds().closeTheDueRound();
        home.clock().advance(Duration.ofMinutes(1));
        Instant later = home.clock().now();
        DecisionNumber broom = raise(SWEEP_STAIRS, "Which broom?");
        home.clock().advance(DecisionRound.WINDOW);
        home.closeDecisionRounds().closeTheDueRound();

        assertThat(roundsClosed()).extracting(event -> event.text("number"), event -> event.text("opened"))
                .containsExactly(
                        tuple(lamp.toString(), opened.toString()),
                        tuple(paint.toString(), opened.toString()),
                        tuple(oil.toString(), opened.toString()),
                        tuple(broom.toString(), later.toString()));
        assertThat(home.wakes().unacknowledged()).filteredOn(queued -> queued.wake().kind() == WakeKind.DECISION_ROUND)
                .extracting(queued -> queued.wake().detail())
                .containsExactly(lamp + " Which lamp?", paint + " Which paint?", oil + " Which oil?", broom + " Which broom?");
    }

    @Test
    void aRoundGathersOnlyTheDecisionsOfRunningShipsThatAreNotUrgent() {
        DecisionNumber lamp = raise(FIT_LAMP, "Which lamp?");
        home.raiseDecision().raise(new TaskReference.ById(PAINT_TOWER), Cards.urgent("Which paint?"), Actor.PRIMARY);
        raise(PLAN_GARDEN, "Which garden?");
        raise(CHOOSE_BULB, "Which bulb?");

        assertThat(home.decisionRounds().gathering()).map(DecisionRound::decisions).contains(List.of(lamp));
    }

    @Test
    void aDecisionBoundToAResearchBoardStaysOutOfTheRoundAndIsAnsweredOnTheBoard() throws IOException {
        DecisionNumber lamp = raise(FIT_LAMP, "Which lamp?");
        DecisionNumber paint = raise(PAINT_TOWER, "Which paint?");
        Worker scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-6"), CategoryId.RESEARCH);
        Path board = Files.writeString(Files.createDirectories(home.home().taskDirectory(scout.id().task())).resolve("board.html"), "<!doctype html>\n");
        home.boardHost().holdsTheNextPollOpen();
        home.armBoard().arm(scout.id(), new TaskReference.ById(scout.id().task()), board, BoardRounds.binding(lamp));
        home.clock().advance(DecisionRound.WINDOW);
        home.closeDecisionRounds().closeTheDueRound();

        assertThat(roundsClosed()).extracting(event -> event.text("number")).containsExactly(paint.toString());
    }

    @Test
    void aDecisionRaisedAgainAfterItsRoundWaitsForTheNextRound() {
        raise(FIT_LAMP, "Which lamp?");
        home.clock().advance(DecisionRound.WINDOW);
        home.closeDecisionRounds().closeTheDueRound();
        home.clock().advance(Duration.ofMinutes(2));
        Instant raisedAgain = home.clock().now();
        DecisionNumber lamp = raise(FIT_LAMP, "Which lamp, now that the tower is taller?");

        assertThat(home.decisionRounds().gathering()).contains(new DecisionRound(raisedAgain, List.of(lamp)));
    }

    @Test
    void aDecisionGoesOutInOneRoundOnly() {
        raise(FIT_LAMP, "Which lamp?");
        home.clock().advance(DecisionRound.WINDOW);
        home.closeDecisionRounds().closeTheDueRound();
        home.clock().advance(DecisionRound.WINDOW);
        home.closeDecisionRounds().closeTheDueRound();

        assertThat(home.decisionRounds().gathering()).isEmpty();
        assertThat(roundsClosed()).hasSize(1);
    }

    private DecisionNumber raise(TaskId task, String title) {
        return home.raiseDecision().raise(new TaskReference.ById(task), Cards.card(title), Actor.PRIMARY);
    }

    private List<LoggedEvent> roundsClosed() {
        return home.events().after(0, 1000).stream().filter(event -> event.isA(DecisionRoundClosed.class)).toList();
    }
}
