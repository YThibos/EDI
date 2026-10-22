package io.github.ythibos.edi.invariant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.task.NewTask;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.PresenterFixtures;
import io.github.ythibos.edi.web.report.CaptainReport;
import io.github.ythibos.edi.web.report.CaptainReportPresenter;
import io.github.ythibos.edi.web.report.ReportWidth;
import io.github.ythibos.edi.web.report.Turn;
import io.github.ythibos.edi.web.report.TurnDocument;
import tools.jackson.databind.json.JsonMapper;

final class ReportGoldenCases {

    static final int TWO_MEGABYTE_TASKS = 512;
    static final int TWO_MEGABYTE_NOTE_WORDS = 819;

    private static final Path GOLDEN_REPORTS = Path.of("src/test/resources/captain-report");
    private static final String REGISTRY = """
            projects:
              - name: HarbourMaster
              - name: Gatehouse
              - name: Lighthouse-Lantern-Kit
              - name: deckhand
              - name: quayside
              - name: sample
              - name: A-project-title-of-forty-one-characters-x
            """;
    private static final String REVIEW_LINK = "https://github.com/example-organisation/example-repository-with-a-long-name/pull/12345";
    private static final LocalDate AFTER_THE_DEMO = LocalDate.of(2026, 10, 20);
    private static final Instant FILED_IN_JULY = Instant.parse("2026-07-01T00:00:00Z");
    private static final JsonMapper JSON = new JsonMapper();

    private ReportGoldenCases() {
    }

    static List<GoldenCase> all() {
        return List.of(
                new GoldenCase("busy", ReportGoldenCases::busy),
                new GoldenCase("cumulative-open", ReportGoldenCases::cumulativeOpen),
                new GoldenCase("cumulative-answered", ReportGoldenCases::cumulativeAnswered),
                new GoldenCase("quiet-backlog", ReportGoldenCases::quietBacklog),
                new GoldenCase("quiet-ongoing", ReportGoldenCases::quietOngoing),
                new GoldenCase("quiet-open-call", ReportGoldenCases::quietOpenCall),
                new GoldenCase("parked-calls", ReportGoldenCases::parkedCalls),
                new GoldenCase("retired-number", ReportGoldenCases::retiredNumber),
                new GoldenCase("project-groups", ReportGoldenCases::projectGroups),
                new GoldenCase("width-default", home -> reviewLink(home, ReportWidth.standard())),
                new GoldenCase("width-60", home -> reviewLink(home, new ReportWidth(60))),
                new GoldenCase("pane-90", home -> reviewLink(home, ReportWidth.forPane(90))),
                new GoldenCase("pane-40", home -> reviewLink(home, ReportWidth.forPane(40))),
                new GoldenCase("pane-318", home -> reviewLink(home, ReportWidth.forPane(318))),
                new GoldenCase("latin-text", ReportGoldenCases::latinText),
                new GoldenCase("wrapping", ReportGoldenCases::wrapping),
                new GoldenCase("large-backlog", home -> queuedBacklog(home, 60, 300)),
                new GoldenCase("two-megabyte-backlog", home -> queuedBacklog(home, TWO_MEGABYTE_TASKS, TWO_MEGABYTE_NOTE_WORDS)));
    }

    static TestHome homeIn(Path directory) throws IOException {
        return TestHome.in(directory).registering(REGISTRY);
    }

    static String rendered(TestHome home, Turn turn, ReportWidth width) {
        return PresenterFixtures.captainReportPresenter().text(new CaptainReport(home.fleetSnapshot().snapshot(), turn, width, Presence.presentSince(TestHome.START)));
    }

    static Shown queuedBacklog(TestHome home, int tasks, int noteWords) {
        String notes = String.join(" ", Collections.nCopies(noteWords, "note"));
        home.storing(IntStream.rangeClosed(1, tasks)
                .mapToObj(item -> Task.queued(new TaskId("large-" + item), shipOn("Queued work item " + item, "sample"), notes, FILED_IN_JULY))
                .toList());
        return Shown.withoutTurn(ReportWidth.standard());
    }

    private static Shown busy(TestHome home) {
        home.continuingDecisionsFrom(52);
        heldForDecision(home, "restart-call", card("Restart the stopped workers", "Both stopped mid-task; nothing is lost."), "HarbourMaster");
        heldForDecision(home, "merge-call", card("Merge the green pull request", ""), "Lighthouse-Lantern-Kit");
        heldForDecision(home, "book-call", card("Book the research minutes", ""), "HarbourMaster");
        heldForDecision(home, "shared-call", card("Share one sign-in setup", ""), "HarbourMaster", "Gatehouse");
        inFlight(home, "backfill", "SHIP HarbourMaster: finish the backfill", "HarbourMaster");
        inFlight(home, "relaunch-fix", "Relaunch fix", "deckhand");
        return new Shown(turn("""
                {"done": [{"projects": [], "text": "Took over monitoring for this session"},
                          {"projects": ["deckhand"], "text": "Report-format investigation finished"}],
                 "next": [{"projects": ["HarbourMaster"], "text": "Start the recovery workers one at a time"}],
                 "tasks": {"backfill": {"text": "Finish the decision-log backfill", "state": "running"},
                           "relaunch-fix": {"text": "Fix the relaunch of a stopped worker", "state": "running"}}}
                """), new ReportWidth(80));
    }

    private static Shown cumulativeOpen(TestHome home) {
        twoOpenCalls(home);
        return new Shown(turn("{\"tasks\": {\"first-call\": {\"text\": \"Reworded but still listed\"}}}"), ReportWidth.standard());
    }

    private static Shown cumulativeAnswered(TestHome home) {
        twoOpenCalls(home);
        answer(home, "first-call", new GivenAnswer("Captain: D1 A", new OptionKey('A'), "", AnswerChannel.CHAT));
        return Shown.withoutTurn(ReportWidth.standard());
    }

    private static Shown quietBacklog(TestHome home) {
        IntStream.rangeClosed(1, 7).forEach(item -> add(home, "queued-" + item, shipOn("Queued work " + item, "deckhand")));
        add(home, "demo-questions", captainItemOn("Questions for the next demo", "HarbourMaster"));
        hold(home, "demo-questions", "keep parked", HoldUntil.day(AFTER_THE_DEMO));
        add(home, "paused-work", shipOn("Paused work", "Gatehouse"));
        hold(home, "paused-work", "waiting on infrastructure", HoldUntil.captainsWord());
        return Shown.withoutTurn(ReportWidth.standard());
    }

    private static Shown quietOngoing(TestHome home) {
        quietBacklog(home);
        inFlight(home, "busy-work", "Busy work", "deckhand");
        return Shown.withoutTurn(ReportWidth.standard());
    }

    private static Shown quietOpenCall(TestHome home) {
        quietOngoing(home);
        home.markDone(new TaskId("busy-work"));
        heldForDecision(home, "open-call", card("An open call", ""));
        return Shown.withoutTurn(ReportWidth.standard());
    }

    private static Shown parkedCalls(TestHome home) {
        heldForDecision(home, "aged-call", card("An old unanswered call", ""), "Gatehouse");
        add(home, "dated-call", captainItemOn("A deferred call", "Gatehouse"));
        hold(home, "dated-call", "revisit after the demo", HoldUntil.day(AFTER_THE_DEMO));
        add(home, "prereq", shipOn("Prerequisite work", "Gatehouse"));
        add(home, "blocked-call", new Intake(TaskKind.CAPTAIN, "Call blocked-call", projects("Gatehouse"), null, null, List.of(new TaskId("prereq")), null));
        raise(home, "blocked-call", card("A blocked call", ""));
        return Shown.withoutTurn(ReportWidth.standard());
    }

    private static Shown retiredNumber(TestHome home) {
        add(home, "infra-work", shipOn("Call infra-work", "Gatehouse"));
        raise(home, "infra-work", card("Pick a path", ""));
        answer(home, "infra-work", new GivenAnswer("Captain: go ahead", null, "", AnswerChannel.CHAT));
        hold(home, "infra-work", "waiting on infra", HoldUntil.captainsWord());
        return Shown.withoutTurn(ReportWidth.standard());
    }

    private static Shown projectGroups(TestHome home) {
        return new Shown(turn("""
                {"done": [{"projects": ["quayside"], "text": "Quayside item"},
                          {"projects": ["Lighthouse-Lantern-Kit"], "text": "Long title item"},
                          {"projects": [], "text": "Fleet item"},
                          {"projects": ["Gatehouse"], "text": "Gatehouse item"},
                          {"projects": ["Gatehouse"], "text": "Second Gatehouse item"}]}
                """), ReportWidth.standard());
    }

    private static Shown reviewLink(TestHome home, ReportWidth width) {
        heldForDecision(home, "link-call", card("Review the pull request", REVIEW_LINK), "deckhand");
        return Shown.withoutTurn(width);
    }

    private static Shown latinText(TestHome home) {
        return new Shown(turn("{\"done\": [{\"text\": \"Café – naïve “quoted” …\"}]}"), ReportWidth.standard());
    }

    private static Shown wrapping(TestHome home) {
        home.continuingDecisionsFrom(57);
        heldForDecision(home, "ledger-call", ledgerCard(), "HarbourMaster", "A-project-title-of-forty-one-characters-x");
        add(home, "tide-tables", shipOn("Print the tide tables for the whole of next season, including the spring tides and the neap tides", "quayside"));
        add(home, "mooring-plan", shipOn("Draw the mooring plan for the new pontoon on the east side of the harbour", "quayside"));
        add(home, "crane-call", new Intake(TaskKind.CAPTAIN, "Call crane-call", projects("quayside"), null, null,
                List.of(new TaskId("tide-tables"), new TaskId("mooring-plan")), null));
        raise(home, "crane-call", card("Rent the crane", ""));
        inFlight(home, "survey", "Survey the breakwater after the storm and list every block that moved more than a hand's width", "HarbourMaster");
        return new Shown(turn("""
                {"done": [{"projects": ["A-project-title-of-forty-one-characters-x"], "text": "Wrote down every reason the ledger needs splitting before the season closes, with the numbers from the last three seasons beside each one"}],
                 "next": [{"projects": ["HarbourMaster", "Gatehouse", "quayside"], "text": "Walk the quay with the harbour master"}],
                 "tasks": {"survey": {"state": "surveying the outer blocks"}}}
                """), new ReportWidth(72));
    }

    private static Intake shipOn(String title, String project) {
        return new Intake(TaskKind.SHIP, title, projects(project), null, null, List.of(), null);
    }

    private static void heldForDecision(TestHome home, String id, DecisionCard card, String... projects) {
        add(home, id, new Intake(TaskKind.CAPTAIN, "Call " + id, projects(projects), null, null, List.of(), null));
        raise(home, id, card);
    }

    private static DecisionCard card(String title, String context) {
        return new DecisionCard(
                title,
                context,
                List.of(option('A', "Take the first path"), option('B', "Take the second path")),
                new Recommendation(new OptionKey('A'), "it keeps the work moving"));
    }

    private static void inFlight(TestHome home, String id, String title, String project) {
        add(home, id, shipOn(title, project));
        home.putInFlight(new TaskId(id));
    }

    private static Turn turn(String document) {
        return TurnDocument.read(JSON.readTree(document));
    }

    private static void twoOpenCalls(TestHome home) {
        heldForDecision(home, "first-call", card("First open call", ""), "Gatehouse");
        heldForDecision(home, "second-call", card("Second open call", ""), "Gatehouse");
    }

    private static void answer(TestHome home, String id, GivenAnswer answer) {
        home.answerDecision().answer(new TaskReference.ById(new TaskId(id)), answer, Actor.PRIMARY);
    }

    private static void add(TestHome home, String id, Intake intake) {
        home.addTask().add(new NewTask(new TaskId(id), intake, ""), Actor.PRIMARY);
    }

    private static Intake captainItemOn(String title, String project) {
        return new Intake(TaskKind.CAPTAIN, title, projects(project), null, null, List.of(), null);
    }

    private static void hold(TestHome home, String id, String reason, HoldUntil until) {
        home.holdTask().hold(new TaskReference.ById(new TaskId(id)), HoldKind.DEFERRAL, reason, until, Actor.PRIMARY);
    }

    private static List<ProjectName> projects(String... names) {
        return Arrays.stream(names).map(ProjectName::new).toList();
    }

    private static void raise(TestHome home, String id, DecisionCard card) {
        home.raiseDecision().raise(new TaskReference.ById(new TaskId(id)), card, Actor.PRIMARY);
    }

    private static DecisionCard ledgerCard() {
        return new DecisionCard(
                "Choose how the harbour master keeps its ledger of every vessel that has called at the quay this season",
                "The ledger grew past what one page can hold, and two ways of splitting it came up during the review of the season so far;"
                        + " see https://example.org/harbour/ledger/season-review-notes-with-a-very-long-address for the notes.",
                List.of(
                        option('A', "Split the ledger by month, so each page covers one calendar month of calls and the archive stays small enough"
                                + " to read in one sitting"),
                        option('B', "Split by vessel"),
                        option('C', "Keep one ledger and add an index page at the front that lists every vessel with the pages where it appears")),
                new Recommendation(new OptionKey('A'), "months match how the captain reviews the season, and the archive of each month never"
                        + " changes once the month has closed, which keeps every earlier page stable"));
    }

    private static DecisionOption option(char key, String text) {
        return new DecisionOption(new OptionKey(key), text);
    }

    @FunctionalInterface
    interface Scenario {

        Shown played(TestHome home);
    }

    record Shown(Turn turn, ReportWidth width) {

        static Shown withoutTurn(ReportWidth width) {
            return new Shown(Turn.NONE, width);
        }
    }

    record GoldenCase(String name, Scenario scenario) {

        String golden() throws IOException {
            return Files.readString(GOLDEN_REPORTS.resolve(name + ".txt"));
        }

        String rendered(Path directory) throws IOException {
            TestHome home = homeIn(directory.resolve(name));
            Shown shown = scenario.played(home);
            return ReportGoldenCases.rendered(home, shown.turn(), shown.width());
        }
    }
}
