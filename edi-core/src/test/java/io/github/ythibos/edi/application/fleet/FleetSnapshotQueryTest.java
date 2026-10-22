package io.github.ythibos.edi.application.fleet;

import static io.github.ythibos.edi.testing.Cards.card;
import static io.github.ythibos.edi.testing.Intakes.captainItem;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static io.github.ythibos.edi.testing.Intakes.shipBlockedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.task.NewTask;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class FleetSnapshotQueryTest {

    private static final MergeRequestAddress REQUEST =
            new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org", "team/harness"), 5);

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void openTheHome() throws IOException {
        home = TestHome.in(directory).registering(Intakes.REGISTRY).continuingDecisionsFrom(57);
    }

    @Test
    void putsOpenWorkWhereItWaits() {
        add(ship("lantern", "Rework the lantern"));
        add(ship("horn", "Tune the horn"));
        add(ship("mast", "Paint the mast"));
        add(captainItem("lamp-choice", "Choose the lamp"));
        home.putInFlight(new TaskId("horn"));
        hold("mast", HoldUntil.day(LocalDate.of(2026, 11, 1)));
        raise("lamp-choice");

        FleetSnapshot fleet = home.fleetSnapshot().snapshot();

        assertThat(fleet.waiting().decisions()).extracting(open -> open.decision().number(), open -> open.task().id())
                .containsExactly(tuple(new DecisionNumber(58), new TaskId("lamp-choice")));
        assertThat(fleet.ongoing()).extracting(ongoing -> ongoing.task().id()).containsExactly(new TaskId("horn"));
        assertThat(fleet.parked()).extracting(parked -> parked.task().id()).containsExactly(new TaskId("mast"));
        assertThat(fleet.queued()).extracting(Task::id).containsExactly(new TaskId("lantern"));
        assertThat(fleet.isQuiet()).isFalse();
    }

    @Test
    void putsADecisionOnABlockedTaskBeforeTheCaptainAndParksTheTaskWithItsNumber() {
        add(ship("lantern", "Rework the lantern"));
        add(shipBlockedBy("lamp-fitting", "Fit the lamp", "lantern"));
        raise("lamp-fitting");

        FleetSnapshot fleet = home.fleetSnapshot().snapshot();

        assertThat(fleet.waiting().decisions()).extracting(open -> open.decision().number()).containsExactly(new DecisionNumber(58));
        assertThat(fleet.waiting().count()).isEqualTo(1);
        assertThat(fleet.parked()).singleElement().satisfies(parked -> {
            assertThat(parked.decision().number()).isEqualTo(new DecisionNumber(58));
            assertThat(parked.blockers()).extracting(Task::id).containsExactly(new TaskId("lantern"));
            assertThat(parked.isBlocked()).isTrue();
        });
    }

    @Test
    void stopsParkingADecisionsTaskOnceItsBlockerIsDone() {
        add(ship("lantern", "Rework the lantern"));
        add(shipBlockedBy("lamp-fitting", "Fit the lamp", "lantern"));
        raise("lamp-fitting");
        home.markDone(new TaskId("lantern"));

        FleetSnapshot fleet = home.fleetSnapshot().snapshot();

        assertThat(fleet.waiting().decisions()).extracting(open -> open.task().id()).containsExactly(new TaskId("lamp-fitting"));
        assertThat(fleet.parked()).isEmpty();
    }

    @Test
    void putsABoardWithQuestionsBeforeTheCaptainUntilARoundAnswersThem() {
        add(ship("lantern", "Rework the lantern"));
        Board armed = home.boards().arm(new TaskId("lantern"), directory.resolve("board.html").toString(), BoardRounds.asking(3), TestHome.START);

        WaitingOnTheCaptain posted = home.fleetSnapshot().snapshot().waiting();
        home.boards().update(armed.answeredOnceMore());
        WaitingOnTheCaptain answered = home.fleetSnapshot().snapshot().waiting();

        assertThat(posted.boards()).singleElement().satisfies(waiting -> {
            assertThat(waiting.task().id()).isEqualTo(new TaskId("lantern"));
            assertThat(waiting.board().openQuestions()).isEqualTo(3);
            assertThat(waiting.board().link()).isEqualTo(BoardRounds.LINK);
        });
        assertThat(posted.count()).isEqualTo(1);
        assertThat(answered.isEmpty()).isTrue();
    }

    @Test
    void putsAnOpenRequestBeforeTheCaptainOnceItsDoneIsAcceptedUntilItIsMerged() {
        Task lamp = WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3");
        Worker worker = WorkerFixtures.servingWorkerOn(home, lamp, CategoryId.IMPLEMENTATION);
        home.deliveries().save(Delivery.of(lamp.id(), requestIn(MergeRequestState.OPEN), TestHome.START));

        WaitingOnTheCaptain beforeDone = home.fleetSnapshot().snapshot().waiting();
        home.workers().update(worker.reported(new StatusReport(StatusState.DONE, null, null, "The lamp is fitted."), TestHome.START));
        WaitingOnTheCaptain afterDone = home.fleetSnapshot().snapshot().waiting();
        home.deliveries().save(home.deliveries().of(lamp.id()).orElseThrow().observed(requestIn(MergeRequestState.MERGED), TestHome.START));
        WaitingOnTheCaptain afterTheMerge = home.fleetSnapshot().snapshot().waiting();

        assertThat(beforeDone.reviews()).isEmpty();
        assertThat(afterDone.reviews()).singleElement().satisfies(ready -> {
            assertThat(ready.task().id()).isEqualTo(lamp.id());
            assertThat(ready.delivery().address().url()).isEqualTo(REQUEST.url());
        });
        assertThat(afterTheMerge.reviews()).isEmpty();
    }

    @Test
    void keepsAnAnsweredDecisionOutOfALaterHold() {
        add(ship("lamp-fitting", "Fit the lamp"));
        raise("lamp-fitting");
        home.answerDecision().answer(new TaskReference.ByDecision(new DecisionNumber(58)),
                new GivenAnswer("D58 A", new OptionKey('A'), "", AnswerChannel.CHAT), Actor.PRIMARY);
        hold("lamp-fitting", HoldUntil.captainsWord());

        FleetSnapshot fleet = home.fleetSnapshot().snapshot();

        assertThat(fleet.waiting().decisions()).isEmpty();
        assertThat(fleet.parked()).singleElement().satisfies(parked -> {
            assertThat(parked.decision()).isNull();
            assertThat(parked.isBlocked()).isFalse();
        });
    }

    @Test
    void parksWorkInFlightBeforeQueuedWorkAndKeepsTheQueueOrder() {
        add(ship("lantern", "Rework the lantern"));
        add(ship("horn", "Tune the horn"));
        add(ship("mast", "Paint the mast"));
        add(ship("deck", "Scrub the deck"));
        hold("lantern", HoldUntil.captainsWord());
        home.putInFlight(new TaskId("mast"));
        hold("mast", HoldUntil.captainsWord());

        FleetSnapshot fleet = home.fleetSnapshot().snapshot();

        assertThat(fleet.parked()).extracting(parked -> parked.task().id()).containsExactly(new TaskId("mast"), new TaskId("lantern"));
        assertThat(fleet.ongoing()).isEmpty();
        assertThat(fleet.queued()).extracting(Task::id).containsExactly(new TaskId("horn"), new TaskId("deck"));
        assertThat(fleet.isQuiet()).isTrue();
    }

    @Test
    void carriesWhatEachOngoingTaskIsDoingTheWorkThatLandedThisWeekAndWhatTheCaptainAnsweredToday() {
        add(ship("horn", "Tune the horn"));
        add(ship("lantern", "Rework the lantern"));
        add(ship("anchor", "Raise the anchor"));
        add(captainItem("lamp-choice", "Choose the lamp"));
        home.putInFlight(new TaskId("horn"));
        home.pipelineRuns().save(new TaskId("horn"), new PipelineRun("01RUN", "running", "review", "abc123", null), TestHome.START);
        home.markDone(new TaskId("anchor"));
        home.clock().advance(Duration.ofDays(8));
        home.markDone(new TaskId("lantern"));
        raise("lamp-choice");
        home.answerDecision().answer(new TaskReference.ByDecision(new DecisionNumber(58)),
                new GivenAnswer("A: the simple way", new OptionKey('A'), "", AnswerChannel.CONSOLE), Actor.CAPTAIN);

        FleetSnapshot fleet = home.fleetSnapshot().snapshot();

        assertThat(fleet.ongoing()).singleElement().satisfies(horn -> {
            assertThat(horn.pipelineRun().activeStep()).isEqualTo("review");
            assertThat(horn.worker()).isNull();
            assertThat(horn.delivery()).isNull();
        });
        assertThat(fleet.landed()).extracting(landed -> landed.task().id()).containsExactly(new TaskId("lantern"));
        assertThat(fleet.answered()).singleElement().satisfies(answered -> {
            assertThat(answered.decision().answer().verbatim()).isEqualTo("A: the simple way");
            assertThat(answered.task().id()).isEqualTo(new TaskId("lamp-choice"));
        });

        home.clock().advance(Duration.ofHours(25));

        assertThat(home.fleetSnapshot().snapshot().answered()).isEmpty();
    }

    private static MergeRequestView requestIn(MergeRequestState state) {
        return new MergeRequestView(REQUEST, state, true, "feature/HRN-3", "main", "2222", state == MergeRequestState.MERGED ? "3333" : null);
    }

    private void add(NewTask task) {
        home.addTask().add(task, Actor.PRIMARY);
    }

    private void hold(String task, HoldUntil until) {
        home.holdTask().hold(new TaskReference.ById(new TaskId(task)), HoldKind.DEFERRAL, "later", until, Actor.PRIMARY);
    }

    private void raise(String task) {
        home.raiseDecision().raise(new TaskReference.ById(new TaskId(task)), card("Which lamp?"), Actor.PRIMARY);
    }
}
