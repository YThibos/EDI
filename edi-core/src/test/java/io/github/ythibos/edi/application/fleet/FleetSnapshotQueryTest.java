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
import io.github.ythibos.edi.domain.event.DomainEvent.StatusReported;
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
    void putsADecisionBoundToAListeningBoardUnderThatBoardEvenOnceTheBoardAsksNothingElse() {
        add(ship("lantern", "Rework the lantern"));
        add(captainItem("lamp-choice", "Choose the lamp"));
        raise("lamp-choice");
        DecisionNumber lamp = home.decisions().ofTask(new TaskId("lamp-choice")).orElseThrow().number();
        Board armed = home.boards().arm(new TaskId("lantern"), directory.resolve("board.html").toString(), BoardRounds.asking(2, lamp), TestHome.START);

        WaitingOnTheCaptain asking = home.fleetSnapshot().snapshot().waiting();
        home.boards().update(armed.answeredOnceMore());
        WaitingOnTheCaptain answeredAllButTheDecision = home.fleetSnapshot().snapshot().waiting();

        assertThat(asking.decisions()).isEmpty();
        assertThat(asking.boards()).singleElement().satisfies(waiting -> assertThat(waiting.decisions()).extracting(open -> open.decision().number())
                .containsExactly(lamp));
        assertThat(asking.count()).isEqualTo(2);
        assertThat(answeredAllButTheDecision.decisions()).isEmpty();
        assertThat(answeredAllButTheDecision.boards()).singleElement().satisfies(waiting -> {
            assertThat(waiting.board().openQuestions()).isZero();
            assertThat(waiting.decisions()).extracting(open -> open.decision().number()).containsExactly(lamp);
        });
    }

    @Test
    void putsADecisionBoundToABoardArmedWithNoOtherQuestionUnderThatBoard() {
        add(ship("lantern", "Rework the lantern"));
        add(captainItem("lamp-choice", "Choose the lamp"));
        raise("lamp-choice");
        DecisionNumber lamp = home.decisions().ofTask(new TaskId("lamp-choice")).orElseThrow().number();
        home.boards().arm(new TaskId("lantern"), directory.resolve("board.html").toString(), BoardRounds.asking(0, lamp), TestHome.START);

        WaitingOnTheCaptain waiting = home.fleetSnapshot().snapshot().waiting();

        assertThat(waiting.decisions()).isEmpty();
        assertThat(waiting.boards()).singleElement().satisfies(board -> assertThat(board.decisions()).extracting(open -> open.decision().number())
                .containsExactly(lamp));
        assertThat(waiting.count()).isEqualTo(1);
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
    void holdsAHandedOverRequestAsChangingWhileItsPipelineRuns() {
        Task lamp = handedOverWithAnOpenRequest();
        home.pipelineRuns().save(lamp.id(), new PipelineRun("01RUN", "running", "review", "abc123", REQUEST.url()), TestHome.START);

        WaitingOnTheCaptain running = home.fleetSnapshot().snapshot().waiting();
        home.pipelineRuns().save(lamp.id(), new PipelineRun("01RUN", "completed", null, "abc123", REQUEST.url()), TestHome.START);
        WaitingOnTheCaptain finished = home.fleetSnapshot().snapshot().waiting();

        assertThat(running.reviews()).isEmpty();
        assertThat(running.changing()).singleElement().satisfies(changing -> {
            assertThat(changing.task().id()).isEqualTo(lamp.id());
            assertThat(changing.delivery().address().url()).isEqualTo(REQUEST.url());
            assertThat(changing.whyChanging()).isEqualTo("pipeline review");
        });
        assertThat(running.count()).isEqualTo(1);
        assertThat(finished.changing()).isEmpty();
        assertThat(finished.reviews()).extracting(ready -> ready.task().id()).containsExactly(lamp.id());
    }

    @Test
    void holdsAHandedOverRequestAsChangingWhileASteerWaitsUnacknowledged() {
        Task lamp = handedOverWithAnOpenRequest();
        home.steerWorker().steer(new TaskReference.ById(lamp.id()), "Amend the lamp's record too.", null, Actor.PRIMARY);
        home.steerWorker().steer(new TaskReference.ById(lamp.id()), "And its history line.", null, Actor.PRIMARY);

        WaitingOnTheCaptain steered = home.fleetSnapshot().snapshot().waiting();
        Worker worker = home.workers().latestOf(lamp.id()).orElseThrow();
        home.workerInbox().acknowledge(worker.id(), 1);
        home.workerInbox().acknowledge(worker.id(), 2);
        WaitingOnTheCaptain acknowledged = home.fleetSnapshot().snapshot().waiting();

        assertThat(steered.reviews()).isEmpty();
        assertThat(steered.changing()).singleElement().satisfies(changing -> assertThat(changing.whyChanging()).isEqualTo("steers 1, 2 not acknowledged"));
        assertThat(acknowledged.changing()).isEmpty();
        assertThat(acknowledged.reviews()).extracting(ready -> ready.task().id()).containsExactly(lamp.id());
    }

    @Test
    void holdsARequestAsChangingOnceItsWorkerIsBackAtWorkButNeverAFirstPassDraft() {
        Task lamp = handedOverWithAnOpenRequest();
        Task shade = WorkerFixtures.queuedShip("hang-shade", "harness", "HRN-4");
        WorkerFixtures.servingWorkerOn(home, shade, CategoryId.IMPLEMENTATION);
        home.deliveries().save(Delivery.of(shade.id(), firstPassDraft(), TestHome.START));
        home.steerWorker().steer(new TaskReference.ById(lamp.id()), "Amend the lamp's record too.", null, Actor.PRIMARY);
        home.workerInbox().acknowledge(home.workers().latestOf(lamp.id()).orElseThrow().id(), 1);
        report(lamp, StatusState.WORKING);

        WaitingOnTheCaptain backAtWork = home.fleetSnapshot().snapshot().waiting();
        report(lamp, StatusState.DONE);
        WaitingOnTheCaptain doneAgain = home.fleetSnapshot().snapshot().waiting();

        assertThat(backAtWork.reviews()).isEmpty();
        assertThat(backAtWork.changing()).singleElement().satisfies(changing -> {
            assertThat(changing.task().id()).isEqualTo(lamp.id());
            assertThat(changing.whyChanging()).isEqualTo("worker back at work");
        });
        assertThat(doneAgain.changing()).isEmpty();
        assertThat(doneAgain.reviews()).extracting(ready -> ready.task().id()).containsExactly(lamp.id());
    }

    @Test
    void keepsARequestWhoseMechanicalDoneAwaitsItsCheckOutOfTheZone() {
        Task lamp = WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3");
        WorkerFixtures.servingWorkerOn(home, lamp, CategoryId.MECHANICAL);
        home.deliveries().save(Delivery.of(lamp.id(), requestIn(MergeRequestState.OPEN), TestHome.START));
        report(lamp, StatusState.DONE);

        WaitingOnTheCaptain checking = home.fleetSnapshot().snapshot().waiting();

        assertThat(checking.reviews()).isEmpty();
        assertThat(checking.changing()).isEmpty();
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
        boolean merged = state == MergeRequestState.MERGED;
        return new MergeRequestView(REQUEST, state, true, "feature/HRN-3", "main", "2222", merged ? "3333" : null, merged ? TestHome.START : null);
    }

    private Task handedOverWithAnOpenRequest() {
        Task lamp = WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3");
        WorkerFixtures.servingWorkerOn(home, lamp, CategoryId.IMPLEMENTATION);
        home.deliveries().save(Delivery.of(lamp.id(), requestIn(MergeRequestState.OPEN), TestHome.START));
        report(lamp, StatusState.DONE);
        return lamp;
    }

    private void report(Task task, StatusState state) {
        StatusReport report = new StatusReport(state, null, null, "Fitting the lamp.");
        Worker reported = home.workers().latestOf(task.id()).orElseThrow().reported(report, TestHome.START);
        home.workers().update(reported);
        home.events().record(new StatusReported(task.id(), reported.id(), report), Actor.worker(reported.id()), TestHome.START);
    }

    private static MergeRequestView firstPassDraft() {
        MergeRequestAddress draft = new MergeRequestAddress(REQUEST.repository(), 6);
        return new MergeRequestView(draft, MergeRequestState.OPEN, true, "feature/HRN-4", "main", "4444", null, null);
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
