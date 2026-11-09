package io.github.ythibos.edi.invariant;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;

import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.application.fleet.FleetSnapshot;
import io.github.ythibos.edi.application.fleet.WaitingOnTheCaptain;
import io.github.ythibos.edi.application.presence.CaptainNotices;
import io.github.ythibos.edi.application.presence.ReturnBrief;
import io.github.ythibos.edi.domain.decision.DecisionRound;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.dispatch.PilotComparison;
import io.github.ythibos.edi.domain.dispatch.ShipMeasure;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.CaptainNotice;
import io.github.ythibos.edi.domain.presence.Mandate;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.tracker.Ticket;
import io.github.ythibos.edi.domain.tracker.TicketDraft;
import io.github.ythibos.edi.domain.tracker.TicketProposal;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Cards;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.PresenterFixtures;
import io.github.ythibos.edi.web.migration.MigrationReportText;
import io.github.ythibos.edi.web.report.CaptainReport;
import io.github.ythibos.edi.web.report.ReportWidth;
import io.github.ythibos.edi.web.report.Turn;
import io.github.ythibos.edi.web.report.TurnReport;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class CaptainFacingStrings {

    private static final JsonMapper JSON = new JsonMapper();
    private static final Set<String> TOO_LARGE_TO_LINT_TWICE = Set.of("large-backlog", "two-megabyte-backlog");
    private static final Instant NOW = TestHome.START.plus(Duration.ofHours(2));
    private static final Path OLD_HOME = Path.of("src/test/resources/fork-home");

    private CaptainFacingStrings() {
    }

    static Stream<Named<GeneratedStrings>> everyKindEdiGenerates() {
        return Stream.concat(goldenReports(), Stream.of(
                kind("the fleet in every view", CaptainFacingStrings::fleetInEveryView),
                kind("cards EDI raises", _ -> cardsEdiRaises()),
                kind("notifications", _ -> notifications()),
                kind("presence words", _ -> presenceWords()),
                kind("migration report", CaptainFacingStrings::migrationReport)));
    }

    private static Stream<Named<GeneratedStrings>> goldenReports() {
        return ReportGoldenCases.all().stream()
                .filter(golden -> !TOO_LARGE_TO_LINT_TWICE.contains(golden.name()))
                .map(golden -> kind("report " + golden.name(), directory -> List.of(golden.rendered(directory))));
    }

    private static Named<GeneratedStrings> kind(String name, GeneratedStrings strings) {
        return Named.of(name, strings);
    }

    private static List<String> fleetInEveryView(Path directory) throws IOException {
        FleetSnapshot fleet = fleetIn(directory).fleetSnapshot().snapshot();
        List<String> views = new ArrayList<>(presences().stream().map(presence -> PresenterFixtures.captainReportPresenter().text(new CaptainReport(fleet, Turn.NONE,
                ReportWidth.standard(), presence, new UnreadableCalendar(NOW, "Outlook did not answer."), null))).toList());
        views.add(PresenterFixtures.returnBriefText().text(new ReturnBrief(Presence.presentSince(TestHome.START).quiet(TestHome.START),
                fleet.waiting(), fleet.landed(), List.of(), fleet.ongoing())));
        views.add(PresenterFixtures.captainReportPresenter().text(new CaptainReport(fleet, Turn.NONE, ReportWidth.standard(),
                Presence.presentSince(TestHome.START), null, null)));
        views.add(PresenterFixtures.turnReportPresenter().text(new TurnReport(fleet.waiting(), Turn.NONE, ReportWidth.standard(),
                new UnreadableCalendar(NOW, "Outlook did not answer."), null, null)));
        views.add(PresenterFixtures.turnReportPresenter().text(new TurnReport(fleet.waiting(), Turn.NONE, ReportWidth.standard(), null, NOW, null)));
        views.add(PresenterFixtures.turnReportPresenter().text(new TurnReport(fleet.waiting(), Turn.NONE, ReportWidth.standard(), null, NOW,
                new DecisionRound(NOW, fleet.waiting().decisions().stream().map(open -> open.decision().number()).toList()))));
        views.add(PresenterFixtures.turnReportPresenter().text(new TurnReport(new WaitingOnTheCaptain(List.of(), List.of(), List.of(), List.of()), Turn.NONE,
                ReportWidth.standard(), null, NOW, null)));
        views.add(JSON.writeValueAsString(PresenterFixtures.sitrepConsolePresenter().present(fleet, NOW, ManualClock.UTC, "laptop", CaptainAddress.DEFAULT,
                Presence.presentSince(NOW).demo(NOW, NOW.plus(Duration.ofHours(1))), _ -> Map.of())));
        return views;
    }

    private static List<String> cardsEdiRaises() {
        TicketDraft draft = new TicketDraft(new ProjectKey("SVC"), "Story", "Fit the lamp", "Brass.", WorkKey.parse("SVC-9"), List.of("lighting"), "Major");
        TicketProposal withCandidates = new TicketProposal(new TaskId("fit-lamp"), draft, List.of(new Ticket(WorkKey.parse("SVC-40"), "Harbour lamps", "To Do")));
        TicketProposal withoutCandidates = new TicketProposal(new TaskId("fit-lamp"), draft, List.of());
        return Stream.of(withCandidates.card(), withoutCandidates.card(), withCandidates.cardAfterRefusal("the tracker no longer finds SVC-40"),
                        new PilotComparison(measured(10, 1_000L, 1, 2), measured(4, 2_000L, 3, 5)).card(),
                        new PilotComparison(measured(10, 3_000L, 4, 6), measured(4, 2_000L, 1, 1)).card(),
                        new PilotComparison(measured(10, 3_000L, 4, 6), List.of()).card())
                .map(card -> card.wording())
                .toList();
    }

    private static List<ShipMeasure> measured(int ships, long tokens, int rounds, int findings) {
        return Stream.iterate(1, number -> number + 1).limit(ships)
                .map(number -> new ShipMeasure(new TaskId("ship-" + number), tokens, rounds, findings, 0))
                .toList();
    }

    private static List<String> notifications() {
        Task ship = WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3");
        Task scout = WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-4");
        List<LoggedEvent> events = List.of(
                event("DecisionRaised", JSON.createObjectNode().put("number", "D58").put("title", "Which lamp?")),
                event("DraftRecorded", JSON.createObjectNode().put("url", "https://github.com/example-owner/harness/pull/7")),
                event("BranchDeliveredToCaptain", JSON.createObjectNode().put("branch", "feature/HRN-3")),
                event("ChangeAppliedToCaptain", JSON.createObjectNode()),
                event("StatusReported", JSON.createObjectNode().put("state", "failed").put("text", "The build broke.")),
                event("StatusReported", JSON.createObjectNode().put("state", "blocked").put("text", "The forge login expired.")),
                event("WorkerDied", JSON.createObjectNode().put("cause", "window-gone")),
                event("LostInOutage", JSON.createObjectNode().put("cause", "machine-restarted")),
                event("WorkerLaunchFailed", JSON.createObjectNode()),
                event("DeferredLaunchFailed", JSON.createObjectNode().put("because", "the copy could not be made")),
                event("BranchDeliveryFailed", JSON.createObjectNode().put("because", "your copy has changes in lamp.txt")),
                event("ChangeNotApplied", JSON.createObjectNode().put("because", "lamp.txt has changes")));
        List<CaptainNotice> notices = new ArrayList<>(events.stream().flatMap(event -> CaptainNotices.of(event, ship).stream()).toList());
        notices.add(CaptainNotices.unreadableCalendar());
        CaptainNotices.of(event("StatusReported", JSON.createObjectNode().put("state", "done").put("text", "Report written.")), scout)
                .ifPresent(notices::add);
        return notices.stream().map(notice -> notice.title() + "\n" + notice.text()).toList();
    }

    private static LoggedEvent event(String type, ObjectNode payload) {
        return new LoggedEvent(1, NOW, Actor.CORE, type, new TaskId("fit-lamp"), payload);
    }

    private static List<String> presenceWords() {
        return presences().stream().map(PresenterFixtures.presenceWords()::of).toList();
    }

    private static List<String> migrationReport(Path directory) throws IOException {
        return List.of(new MigrationReportText().text(TestHome.in(directory).migrateFirstofficer().dryRun(OLD_HOME.toAbsolutePath())));
    }

    private static List<Presence> presences() {
        Presence present = Presence.presentSince(TestHome.START);
        return List.of(present, present.quiet(NOW), present.quietAfterSilence(TestHome.START, Duration.ofMinutes(15), NOW),
                present.quietForAMeeting(NOW.minusSeconds(60), TestHome.START, NOW), present.away(new Mandate("Carry on with the lamp.", 2), NOW),
                present.demo(NOW, NOW.plus(Duration.ofHours(1))));
    }

    private static TestHome fleetIn(Path directory) throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        home.storing(List.of(WorkerFixtures.queued("choose-lamp", TaskKind.CAPTAIN, "harness", null), WorkerFixtures.queuedShip("tune-horn", "harness", "HRN-4")));
        home.raiseDecision().raise(new TaskReference.ById(new TaskId("choose-lamp")), Cards.card("Which lamp?"), Actor.PRIMARY);
        home.boards().arm(new TaskId("tune-horn"), directory.resolve("board.html").toString(), BoardRounds.asking(2), TestHome.START);
        readyForReview(home, lamp);
        return home;
    }

    private static void readyForReview(TestHome home, Worker lamp) {
        home.workers().update(home.workers().get(lamp.id()).orElseThrow().reported(new StatusReport(StatusState.DONE, null, null, "Fitted."), NOW));
        MergeRequestAddress request = new MergeRequestAddress(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), 7);
        home.deliveries().save(Delivery.of(lamp.id().task(),
                new MergeRequestView(request, MergeRequestState.OPEN, true, "feature/HRN-3", "main", "abc123", null, null), NOW));
    }

    @FunctionalInterface
    interface GeneratedStrings {

        List<String> in(Path directory) throws IOException;
    }
}
