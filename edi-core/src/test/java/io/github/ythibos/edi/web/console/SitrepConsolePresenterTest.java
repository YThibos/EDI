package io.github.ythibos.edi.web.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.fleet.ChangingRequest;
import io.github.ythibos.edi.application.fleet.DealtWith;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.LandedTask;
import io.github.ythibos.edi.application.fleet.OngoingTask;
import io.github.ythibos.edi.application.fleet.OpenDecision;
import io.github.ythibos.edi.application.fleet.ParkedTask;
import io.github.ythibos.edi.application.fleet.ReadyForReview;
import io.github.ythibos.edi.application.fleet.TaskLinks;
import io.github.ythibos.edi.application.fleet.WaitingBoard;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.application.fleet.WorkerQuestion;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.DeliveryNote;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.HeadChecks;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.BootId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.UsageLimit;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.web.PresenterFixtures;
import tools.jackson.databind.json.JsonMapper;

class SitrepConsolePresenterTest {

    private static final Instant NOW = PresenterFixtures.AT.plusSeconds(3_600);
    private static final MergeRequestAddress DRAFT = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 12);

    private static final WaitingOnTheCaptain NOTHING_WAITING = new WaitingOnTheCaptain(List.of(), List.of(), List.of(), List.of(), List.of());

    private final SitrepConsolePresenter presenter = PresenterFixtures.sitrepConsolePresenter();

    @Test
    void presentsEachDecisionWithItsOptionsAndRecommendationAndEachAnsweredOneUntilItIsTicked() {
        DealtWith answered = new DealtWith(List.of(new DealtWith.Answered(PresenterFixtures.answeredDecision(), PresenterFixtures.captainItem())), List.of(),
                List.of());
        ConsoleSitrep sitrep = presented(new FleetSnapshot(theLampDecision(), answered, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.schema()).isEqualTo("edi-sitrep.v1");
        assertThat(sitrep.address()).isEqualTo("Commander");
        assertThat(sitrep.machine()).isEqualTo("laptop");
        assertThat(sitrep.decisions()).singleElement().satisfies(card -> {
            assertThat(card.number()).isEqualTo("D58");
            assertThat(card.task()).isEqualTo("lamp-choice");
            assertThat(card.title()).isEqualTo("Which lamp?");
            assertThat(card.options()).extracting(ConsoleSitrep.Option::key, ConsoleSitrep.Option::text)
                    .containsExactly(tuple("A", "The simple way"), tuple("B", "The thorough way"));
            assertThat(card.recommended()).isEqualTo(new ConsoleSitrep.Recommendation("A", "it is enough"));
            assertThat(card.stops()).isNull();
        });
        assertThat(sitrep.dealtWith().decisions()).singleElement().satisfies(answer -> {
            assertThat(answer.call()).isEqualTo("D58");
            assertThat(answer.verbatim()).isEqualTo("D58, A\nand keep the old one");
            assertThat(answer.option()).isEqualTo("A");
            assertThat(answer.channel()).isEqualTo("chat");
        });
    }

    @Test
    void tellsWhatEachOngoingTaskIsDoingFromItsWorkerAndItsPipeline() {
        Task horn = ship("horn", "Tune the horn");
        Task bell = ship("bell", "Polish the bell");
        Task mast = ship("mast", "Paint the mast");
        Worker reporting = workerOn(horn).reported(new StatusReport(StatusState.WORKING, null, null, "Fitting the brass."), NOW);
        PipelineRun reviewing = new PipelineRun("01RUN", "running", "review", false, "abc123", null, false);

        ConsoleSitrep sitrep = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(
                new OngoingTask(horn, reporting, null, delivery(horn)),
                new OngoingTask(bell, workerOn(bell), reviewing, null),
                new OngoingTask(mast, null, null, null)), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.ongoing()).extracting(ConsoleSitrep.Ongoing::task, ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing,
                ConsoleSitrep.Ongoing::draft).containsExactly(
                        tuple("horn", "working", "Fitting the brass.", DRAFT.url()),
                        tuple("bell", "starting", "pipeline review", null),
                        tuple("mast", "waiting", "no worker yet", null));
    }

    @Test
    void labelsAWorkersQuestionOrBlockAsEdisAndShowsItSettledOnceTheSteerIsAcknowledged() {
        Task horn = ship("horn", "Tune the horn");
        Task bell = ship("bell", "Polish the bell");
        Task mast = ship("mast", "Paint the mast");
        StatusKey reviewGate = new StatusKey("nm-01RUN-review");
        Worker gated = workerOn(horn).reported(new StatusReport(StatusState.NEEDS_DECISION, reviewGate, null, "Fix the review finding or skip it?"), NOW);
        Worker blocked = workerOn(bell).reported(new StatusReport(StatusState.BLOCKED, new StatusKey("polish"), null, "The polish is gone."), NOW);
        Worker settled = workerOn(mast).reported(new StatusReport(StatusState.NEEDS_DECISION, reviewGate, null, "Fix or skip?"), NOW)
                .settledBy(InboxMessage.sent(mast.id(), 1, "Fix it.", reviewGate, NOW), NOW);

        ConsoleSitrep sitrep = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(
                new OngoingTask(horn, gated, null, null),
                new OngoingTask(bell, blocked, null, null),
                new OngoingTask(mast, settled, null, null)), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.ongoing()).extracting(ConsoleSitrep.Ongoing::task, ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing).containsExactly(
                tuple("horn", "with me", "I am settling a question"),
                tuple("bell", "with me", "I am helping it past a block"),
                tuple("mast", "working", "back at work; I settled its question"));
    }

    @Test
    void leadsEachOngoingTaskWithItsWorkKeyOnceAndWithNothingForAnUnkeyedTask() {
        Task horn = keyed(ship("horn", "Tune the horn"), "EDI-31");
        Task bell = keyed(ship("bell", "DOCK-58: rejected request keeps its reason"), "DOCK-58");
        Task buoy = keyed(ship("buoy", "EDI-310 Paint the buoy"), "EDI-31");
        Task mast = ship("mast", "Paint the mast");

        ConsoleSitrep sitrep = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(
                new OngoingTask(horn, null, null, null),
                new OngoingTask(bell, null, null, null),
                new OngoingTask(buoy, null, null, null),
                new OngoingTask(mast, null, null, null)), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.ongoing()).extracting(ConsoleSitrep.Ongoing::key, ConsoleSitrep.Ongoing::title).containsExactly(
                tuple("EDI-31", "Tune the horn"),
                tuple("DOCK-58", "rejected request keeps its reason"),
                tuple("EDI-31", "EDI-310 Paint the buoy"),
                tuple(null, "Paint the mast"));
    }

    @Test
    void presentsTheBoardsAndTheRequestsThatWaitOnTheCaptain() {
        Task research = ship("lamp-research", "Research the lamp");
        Task lamp = ship("fit-lamp", "Fit the lamp");
        Board board = new Board(new BoardId(3), research.id(), "/tmp/board.html", BoardRounds.LINK, List.of(), 0, 2, NOW, null, null);
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(), List.of(new WaitingBoard(board, research, List.of())),
                List.of(new ReadyForReview(lamp, delivery(lamp))), List.of(), List.of());

        ConsoleSitrep sitrep = presented(new FleetSnapshot(waiting, DealtWith.NOTHING, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.boards()).containsExactly(new ConsoleSitrep.Board("lamp-research", "Research the lamp", List.of("harness"), 2, BoardRounds.LINK,
                List.of()));
        assertThat(sitrep.reviews()).containsExactly(new ConsoleSitrep.Review("fit-lamp", "Fit the lamp", List.of("harness"), DRAFT.url(), null,
                new ConsoleSitrep.AfterLanding(List.of(), null)));
    }

    @Test
    void namesTheWorkADecisionStopsWhenItsWorkerWaitsForIt() {
        Task horn = keyed(ship("horn", "EDI-31 Tune the horn"), "EDI-31");
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(new OpenDecision(PresenterFixtures.openDecision(), horn, true)), List.of(),
                List.of(), List.of(), List.of());

        ConsoleSitrep sitrep = presented(new FleetSnapshot(waiting, DealtWith.NOTHING, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.decisions()).extracting(ConsoleSitrep.Card::stops).containsExactly("EDI-31 Tune the horn");
    }

    @Test
    void carriesTheStepsAfterLandingAndTheMergeOrderFromEachReviewsDeliveryNote() {
        Task lamp = ship("fit-lamp", "Fit the lamp");
        Task bell = ship("bell", "Polish the bell");
        Delivery merged = new Delivery(bell.id(), DRAFT, false, "abc123", HeadChecks.PASSED, MergeRequestState.MERGED, "def456", null, NOW, NOW);
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(), List.of(), List.of(new ReadyForReview(lamp, delivery(lamp))), List.of(), List.of());
        DealtWith settled = new DealtWith(List.of(), List.of(), List.of(new DealtWith.SettledRequest(bell, merged)));
        DeliveryNote note = new DeliveryNote(List.of("Run edi up."), "Merges after #329.");
        List<TaskId> asked = new ArrayList<>();

        ConsoleSitrep sitrep = presenter.present(new FleetSnapshot(waiting, settled, List.of(), List.of(), List.of(), List.of(), List.of()), NOW,
                ManualClock.UTC, "laptop", CaptainAddress.DEFAULT, Presence.presentSince(NOW), _ -> Map.of(), tasks -> {
                    asked.addAll(tasks);
                    return Map.of(lamp.id(), note);
                });

        assertThat(asked).containsExactly(lamp.id(), bell.id());
        assertThat(sitrep.reviews()).extracting(ConsoleSitrep.Review::afterLanding)
                .containsExactly(new ConsoleSitrep.AfterLanding(List.of("Run edi up."), "Merges after #329."));
        assertThat(sitrep.dealtWith().reviews()).containsExactly(new ConsoleSitrep.SettledReview("review:bell", "bell", "Polish the bell",
                List.of("harness"), DRAFT.url(), "merged", new ConsoleSitrep.AfterLanding(List.of(), null)));
    }

    @Test
    void listsAListeningBoardWithItsRoundAnsweredAndAnEndedBoardUnderTheirCallsUntilTheyAreTicked() {
        Task research = ship("lamp-research", "Research the lamp");
        Board listening = new Board(new BoardId(2), research.id(), "/tmp/listening.html", BoardRounds.LINK, List.of(), 1, 0, NOW, null, null);
        Board ended = new Board(new BoardId(3), research.id(), "/tmp/board.html", BoardRounds.LINK, List.of(), 1, 0, NOW, NOW, null);

        ConsoleSitrep sitrep = presented(new FleetSnapshot(NOTHING_WAITING, new DealtWith(List.of(),
                List.of(new DealtWith.SettledBoard(listening, research), new DealtWith.SettledBoard(ended, research)), List.of()), List.of(), List.of(),
                List.of(), List.of(), List.of()));

        assertThat(sitrep.dealtWith().boards()).containsExactly(
                new ConsoleSitrep.SettledBoard("board:2", "lamp-research", "Research the lamp", List.of("harness"), BoardRounds.LINK, "round answered"),
                new ConsoleSitrep.SettledBoard("board:3", "lamp-research", "Research the lamp", List.of("harness"), BoardRounds.LINK, "the board ended"));
    }

    @Test
    void marksARequestStillChangingAsOngoingAndComingBackWithWhyInsteadOfAsReady() {
        Task lamp = ship("fit-lamp", "Fit the lamp");
        PipelineRun running = new PipelineRun("01RUN", "running", null, false, "abc123", DRAFT.url(), false);
        ChangingRequest changing = new ChangingRequest(lamp, delivery(lamp), false, running, List.of(2));

        ConsoleSitrep sitrep = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(new OngoingTask(lamp, workerOn(lamp), running,
                delivery(lamp))), List.of(changing), List.of(), List.of(), List.of()));

        assertThat(sitrep.reviews()).isEmpty();
        assertThat(sitrep.ongoing()).extracting(ConsoleSitrep.Ongoing::task, ConsoleSitrep.Ongoing::comingBack)
                .containsExactly(tuple("fit-lamp", "still a draft; pipeline running; steer 2 not acknowledged"));
    }

    @Test
    void saysTheFleetStandsStillOnTheUsageLimitUntilItsFirstResetAndOnlyWhileAWorkerIsParkedOnIt() {
        Task horn = ship("horn", "Tune the horn");
        Task bell = ship("bell", "Polish the bell");
        Instant firstReset = NOW.plus(Duration.ofHours(1));
        Worker parked = workerOn(horn).promptSubmitted(NOW).parkedOn(new UsageLimit("session limit", firstReset), NOW);
        Worker alsoParked = workerOn(bell).promptSubmitted(NOW).parkedOn(new UsageLimit("session limit", firstReset.plus(Duration.ofHours(1))), NOW);

        ConsoleSitrep stopped = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(new OngoingTask(horn, parked, null, null),
                new OngoingTask(bell, alsoParked, null, null)), List.of(), List.of(), List.of(), List.of()));
        ConsoleSitrep resumed = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(new OngoingTask(horn,
                parked.promptSubmitted(firstReset), null, null)), List.of(), List.of(), List.of(), List.of()));

        assertThat(stopped.usageLimit()).isEqualTo(new ConsoleSitrep.LimitStop(firstReset, List.of("horn", "bell")));
        assertThat(stopped.ongoing()).extracting(ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::doing, ConsoleSitrep.Ongoing::limitResetsAt)
                .containsExactly(tuple("usage limit", "stopped on the session limit", firstReset),
                        tuple("usage limit", "stopped on the session limit", firstReset.plus(Duration.ofHours(1))));
        assertThat(resumed.usageLimit()).isNull();
        assertThat(resumed.ongoing()).extracting(ConsoleSitrep.Ongoing::state, ConsoleSitrep.Ongoing::limitResetsAt).containsExactly(tuple("busy", null));
    }

    @Test
    void presentsAWorkersOpenQuestionInTheZoneWithWhenItWasAsked() {
        Task horn = ship("horn", "Tune the horn");
        Instant asked = NOW.minus(Duration.ofMinutes(20));
        Worker gated = workerOn(horn).reported(new StatusReport(StatusState.NEEDS_DECISION, new StatusKey("pitch"), null, "Which pitch?"), asked);
        WaitingOnTheCaptain waiting = new WaitingOnTheCaptain(List.of(), List.of(), List.of(),
                List.of(new WorkerQuestion(horn, gated, Duration.ofMinutes(20))), List.of());

        ConsoleSitrep sitrep = presented(new FleetSnapshot(waiting, DealtWith.NOTHING, List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThat(sitrep.workerQuestions()).containsExactly(new ConsoleSitrep.Question("horn", "Tune the horn", List.of("harness"), "Which pitch?", asked));
    }

    @Test
    void chartsQueuedAndParkedWorkInQueueOrderAndCountsWhatItLeavesOut() {
        List<Task> queued = IntStream.rangeClosed(1, 22).mapToObj(index -> ship("work-" + index, "Queued work " + index, PresenterFixtures.AT.plusSeconds(index)))
                .toList();
        ParkedTask parked = new ParkedTask(PresenterFixtures.heldHorn(), null, List.of());

        ConsoleSitrep sitrep = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(), List.of(), List.of(parked), queued, List.of()));

        assertThat(sitrep.chartedTotal()).isEqualTo(23);
        assertThat(sitrep.charted()).hasSize(SitrepConsolePresenter.CHARTED_SHOWN);
        assertThat(sitrep.charted()).extracting(ConsoleSitrep.Charted::task).startsWith("fog-horn", "work-1", "work-2").endsWith("work-19");
        assertThat(presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(), List.of(), List.of(parked), List.of(), List.of())).charted())
                .singleElement()
                .extracting(ConsoleSitrep.Charted::title, ConsoleSitrep.Charted::waiting)
                .containsExactly("Tune the horn", "deferred until 2026-11-01");
    }

    @Test
    void listsRecentlyLandedWorkWithItsDraftAndCountsItAll() {
        List<LandedTask> landed = IntStream.rangeClosed(1, 12).mapToObj(index -> landed("landed-" + index)).toList();

        ConsoleSitrep sitrep = presented(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(), List.of(), List.of(), List.of(), landed));

        assertThat(sitrep.landedTotal()).isEqualTo(12);
        assertThat(sitrep.landed()).hasSize(SitrepConsolePresenter.LANDED_SHOWN);
        assertThat(sitrep.landed().getFirst().draft()).isEqualTo(DRAFT.url());
    }

    @Test
    void carriesTheLinksOfEveryTaskItShowsKeyedByTask() {
        Task horn = ship("horn", "Tune the horn");
        TaskLinks hornLinks = new TaskLinks("SVC-7", "https://jira.example.org/browse/SVC-7", null, null, null, null, List.of("D58"));
        List<Task> asked = new ArrayList<>();

        ConsoleSitrep sitrep = presenter.present(new FleetSnapshot(NOTHING_WAITING, DealtWith.NOTHING, List.of(new OngoingTask(horn, null, null, null)), List.of(), List.of(), List.of(),
                List.of()), NOW, ManualClock.UTC, "laptop", CaptainAddress.DEFAULT, Presence.presentSince(NOW), tasks -> {
                    asked.addAll(tasks);
                    return Map.of(horn.id(), hornLinks);
                }, _ -> Map.of());

        assertThat(asked).extracting(Task::id).containsExactly(horn.id());
        assertThat(sitrep.links()).containsExactly(Map.entry("horn", hornLinks));
    }

    @Test
    void writesTheDocumentTheConsoleReads() {
        ConsoleSitrep sitrep = presented(new FleetSnapshot(theLampDecision(), DealtWith.NOTHING, List.of(), List.of(), List.of(), List.of(), List.of()));

        String json = new JsonMapper().writeValueAsString(sitrep);

        assertThat(json).contains("\"schema\":\"edi-sitrep.v1\"", "\"generated\":\"2026-10-15T10:30:00Z\"", "\"zone\":\"UTC\"", "\"number\":\"D58\"",
                "\"recommended\":{\"key\":\"A\",\"why\":\"it is enough\"}", "\"boards\":[]", "\"reviews\":[]", "\"workerQuestions\":[]", "\"landedTotal\":0", "\"chartedTotal\":0");
    }

    private static WaitingOnTheCaptain theLampDecision() {
        return new WaitingOnTheCaptain(List.of(new OpenDecision(PresenterFixtures.openDecision(), PresenterFixtures.captainItem(), false)), List.of(), List.of(), List.of(), List.of());
    }

    private ConsoleSitrep presented(FleetSnapshot fleet) {
        return presenter.present(fleet, NOW, ManualClock.UTC, "laptop", CaptainAddress.DEFAULT, Presence.presentSince(NOW), _ -> Map.of(), _ -> Map.of());
    }

    private static Task ship(String id, String title) {
        return ship(id, title, PresenterFixtures.AT);
    }

    private static Task ship(String id, String title, Instant filed) {
        return Task.queued(new TaskId(id), new Intake(TaskKind.SHIP, title, List.of(new ProjectName("harness")), null, null, List.of(), null, null), "", filed);
    }

    private static Task keyed(Task task, String key) {
        return task.keyedOn(WorkKey.parse(key));
    }

    private static Worker workerOn(Task task) {
        return Worker.launched(WorkerId.first(task.id()), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH), new WorkerWindow("edi", 1L, PresenterFixtures.AT, "@1", 2L),
                new WorkerLaunch(LaunchKind.DISPATCH, PresenterFixtures.AT, new BootId("boot"), LoginName.DEFAULT));
    }

    private static Delivery delivery(Task task) {
        return new Delivery(task.id(), DRAFT, true, "abc123", HeadChecks.UNDECIDED, MergeRequestState.OPEN, null, null, NOW, NOW);
    }

    private static LandedTask landed(String id) {
        Task task = ship(id, "Landed " + id);
        return new LandedTask(task, delivery(task));
    }
}
