package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

@Tag("adapter")
class CommandsTest {

    @TempDir
    private Path home;

    private StubCore core;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home);
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void readsTheBacklogAsTextWithTheHomeToken() {
        core.answering(200, "Queued (0)\n");

        int exitCode = edi("", "backlog", "--ready");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("Queued (0)\n");
        assertThat(core.received()).isEqualTo(new StubCore.Request("GET", "/api/backlog?ready=true", "Bearer stub-token", "text/plain", null, null, ""));
    }

    @Test
    void asksForJsonWhenToldAndEndsItsLine() {
        core.answering(200, "{\"entries\":[]}");

        edi("", "task", "list", "--state", "in-flight", "--json");

        assertThat(out).hasToString("{\"entries\":[]}\n");
        assertThat(core.received().target()).isEqualTo("/api/tasks?state=in-flight");
        assertThat(core.received().accepted()).isEqualTo("application/json");
    }

    @Test
    void addsATaskWithItsNotesFromStandardInput() {
        core.answering(201, "Queued fit-lamp.\n");

        int exitCode = edi("Bring the \"long\" ladder.\nTwo people.", "task", "add", "--kind", "ship", "--project", "harness,garden",
                "--title", "Fit the lamp", "--key", "HRN-3", "--blocked-by", "lantern", "--body", "-");

        assertThat(exitCode).isZero();
        assertThat(core.received().method()).isEqualTo("POST");
        assertThat(core.received().target()).isEqualTo("/api/tasks");
        assertThat(core.received().body()).isEqualTo("{\"kind\":\"ship\",\"projects\":[\"harness\",\"garden\"],\"title\":\"Fit the lamp\","
                + "\"key\":\"HRN-3\",\"blockedBy\":[\"lantern\"],\"body\":\"Bring the \\\"long\\\" ladder.\\nTwo people.\"}");
    }

    @Test
    void sendsTheDetailsOfTheTicketATaskWouldBeProposed() {
        core.answering(201, "Queued fix-lamps; D59 asks which ticket it books on, and it waits for that answer.\n");

        int exitCode = edi("", "task", "add", "--kind", "ship", "--project", "service", "--title", "Fix the lamps", "--issue-type", "Bug",
                "--epic", "SVC-9", "--labels", "lighting,harbour", "--priority", "Major");

        assertThat(exitCode).isZero();
        assertThat(core.received().body()).isEqualTo("{\"kind\":\"ship\",\"projects\":[\"service\"],\"title\":\"Fix the lamps\",\"issueType\":\"Bug\","
                + "\"epic\":\"SVC-9\",\"labels\":[\"lighting\",\"harbour\"],\"priority\":\"Major\"}");
    }

    @Test
    void showsNotesHoldsAndReleasesATask() {
        core.answering(200, "ok\n");

        edi("", "task", "show", "D58");
        assertThat(core.received().target()).isEqualTo("/api/tasks/D58");

        edi("The ladder is ready.\n", "task", "note", "fit-lamp");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/notes");
        assertThat(core.received().body()).isEqualTo("{\"note\":\"The ladder is ready.\\n\"}");

        edi("", "task", "key", "fit-lamp", "SVC-40");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/key");
        assertThat(core.received().body()).isEqualTo("{\"key\":\"SVC-40\"}");

        edi("", "task", "key", "D87", "SVC-40", "--message", "8");
        assertThat(core.received().target()).isEqualTo("/api/tasks/D87/key");
        assertThat(core.received().body()).isEqualTo("{\"key\":\"SVC-40\",\"message\":8}");

        edi("", "hold", "fit-lamp", "--kind", "deferral", "--reason", "after the storm", "--until", "2026-11-01");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/hold");
        assertThat(core.received().body()).isEqualTo("{\"kind\":\"deferral\",\"reason\":\"after the storm\",\"until\":\"2026-11-01\"}");

        edi("", "release", "fit-lamp");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/release");
        assertThat(core.received().body()).isEqualTo("{}");
    }

    @Test
    void raisesADecisionFromItsCardAndPrintsTheNumber() {
        core.answering(201, "D58\n");
        String card = "{\"title\":\"Which lamp?\",\"options\":[{\"key\":\"A\",\"text\":\"LED\"}],\"recommend\":{\"key\":\"A\",\"why\":\"lasts\"}}";

        int exitCode = edi(card, "decision", "raise", "lamp-choice", "--card", "-");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("D58\n");
        assertThat(core.received().target()).isEqualTo("/api/tasks/lamp-choice/decision");
        assertThat(core.received().body()).isEqualTo(card);
    }

    @Test
    void readsACardFromAFile() throws IOException {
        core.answering(201, "D58\n");
        Path card = Files.writeString(home.resolve("card.json"), "{\"title\":\"From a file\"}");

        edi("", "decision", "raise", "lamp-choice", "--card", card.toString());

        assertThat(core.received().body()).isEqualTo("{\"title\":\"From a file\"}");
    }

    @Test
    void saysWhenACardFileCannotBeRead() {
        int exitCode = edi("", "decision", "raise", "lamp-choice", "--card", home.resolve("missing.json").toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith(home.resolve("missing.json") + " cannot be read: ");
        assertThat(core.received()).isNull();
    }

    @Test
    void recordsTheCaptainsAnswerVerbatim() {
        core.answering(200, "Recorded the answer to D58 (option A); lamp-choice is done.\n");

        edi("", "decision", "answer", "D58", "--option", "A", "--text", "  D58, A\tplease ", "--interpretation", "The LED.");

        assertThat(core.received().target()).isEqualTo("/api/decisions/D58/answer");
        assertThat(core.received().body()).isEqualTo("{\"text\":\"  D58, A\\tplease \",\"option\":\"A\",\"interpretation\":\"The LED.\"}");
    }

    @Test
    void recordsTheCaptainsAnswerFromAStoredMessage() {
        edi("", "decision", "answer", "D58", "--option", "B", "--message", "7", "--line", "1");

        assertThat(core.received().body()).isEqualTo("{\"message\":7,\"line\":1,\"option\":\"B\"}");
    }

    @Test
    void catchesASparkInOneStepListsTheOpenOnesAndSortsEach() {
        core.answering(201, "Caught spark 1.\n");

        assertThat(edi("", "spark", "A tide clock on the \"harbour\" wall")).isZero();
        assertThat(core.received().target()).isEqualTo("/api/sparks");
        assertThat(core.received().body()).isEqualTo("{\"text\":\"A tide clock on the \\\"harbour\\\" wall\"}");

        edi("Log the lamp's hours.\n", "spark", "-", "--task", "fit-lamp");
        assertThat(core.received().body()).isEqualTo("{\"text\":\"Log the lamp's hours.\\n\",\"task\":\"fit-lamp\"}");

        edi("", "spark", "Paint it blue", "--project", "harness");
        assertThat(core.received().body()).isEqualTo("{\"text\":\"Paint it blue\",\"project\":\"harness\"}");

        edi("", "spark", "list", "--json");
        assertThat(core.received()).extracting(StubCore.Request::method, StubCore.Request::target, StubCore.Request::accepted)
                .containsExactly("GET", "/api/sparks", "application/json");

        edi("", "spark", "backlog", "1", "EDI-190");
        assertThat(core.received()).extracting(StubCore.Request::target, StubCore.Request::body)
                .containsExactly("/api/sparks/1/backlog", "{\"key\":\"EDI-190\"}");

        edi("", "spark", "fold", "2", "EDI-120");
        assertThat(core.received()).extracting(StubCore.Request::target, StubCore.Request::body)
                .containsExactly("/api/sparks/2/fold", "{\"key\":\"EDI-120\"}");

        edi("", "spark", "drop", "3", "--reason", "One is enough.");
        assertThat(core.received()).extracting(StubCore.Request::target, StubCore.Request::body)
                .containsExactly("/api/sparks/3/drop", "{\"reason\":\"One is enough.\"}");
    }

    @Test
    void asksForTheSparksIdeaOrASubcommand() {
        assertThat(edi("", "spark")).isEqualTo(2);
        assertThat(err.toString()).startsWith("Name the idea, or a subcommand; edi spark --help lists them.");
        assertThat(core.received()).isNull();
    }

    @Test
    void listsDecisionsAndProjects() {
        core.answering(200, "Open decisions (0)\n");

        edi("", "decision", "list", "--open");
        assertThat(core.received().target()).isEqualTo("/api/decisions?open=true");

        edi("", "project", "list");
        assertThat(core.received().target()).isEqualTo("/api/projects");
    }

    @Test
    void checksTheOutgoingCommitsOfTheWorkingDirectory() {
        core.answering(200, "Clear: no secret, no personal material and no -SNAPSHOT version in @{upstream}..HEAD\n");

        int exitCode = edi("", "publish-check");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/publish-checks");
        assertThat(core.received().body()).isEqualTo("{\"repository\":\"" + Path.of("").toAbsolutePath() + "\",\"range\":\"@{upstream}..HEAD\"}");
    }

    @Test
    void plansTheMigrationFromAnAbsoluteHome() {
        core.answering(200, "Dry run\n");

        int exitCode = edi("", "migrate", "firstofficer", "--from", "relative/old home", "--dry-run");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/migrations/firstofficer/plan?from="
                + CoreExchange.encoded(Path.of("relative/old home").toAbsolutePath().normalize().toString()));
        assertThat(core.received().target()).doesNotContain(" ");
    }

    @Test
    void importsTheOldHomeForRealAndOverwritesOnlyWhenTold() {
        core.answering(200, "Imported\n");

        int exitCode = edi("", "migrate", "firstofficer", "--from", "/old", "--overwrite");

        assertThat(exitCode).isZero();
        assertThat(core.received().method()).isEqualTo("POST");
        assertThat(core.received().target()).isEqualTo("/api/migrations/firstofficer");
        assertThat(core.received().body()).isEqualTo("{\"from\":\"/old\",\"overwrite\":true}");
    }

    @Test
    void refusesADryRunThatWouldOverwrite() {
        int exitCode = edi("", "migrate", "firstofficer", "--from", "/old", "--dry-run", "--overwrite");

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).contains("A dry run changes nothing, so it takes no --overwrite.");
        assertThat(core.received()).isNull();
    }

    @Test
    void adoptsATaskOfTheOldHomeByItsBranchAndCopy() {
        core.answering(201, "Took over\n");

        int exitCode = edi("", "task", "adopt", "lantern-rework", "--from", "/old", "--branch", "feature/LHT-3", "--worktree", "pool/1/lighthouse",
                "--session", "0f6c1d2e-3a4b-4c5d-8e9f-a0b1c2d3e4f5");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/tasks/lantern-rework/adopt");
        assertThat(core.received().body()).isEqualTo("{\"from\":\"/old\",\"branch\":\"feature/LHT-3\",\"worktree\":\""
                + Path.of("pool/1/lighthouse").toAbsolutePath().normalize() + "\",\"session\":\"0f6c1d2e-3a4b-4c5d-8e9f-a0b1c2d3e4f5\"}");
    }

    @Test
    void adoptsInTheSpecificationsShapeLeavingTheOldHomeToTheCore() {
        core.answering(201, "Took over\n");

        int exitCode = edi("", "task", "adopt", "lantern-rework", "--branch", "feature/LHT-3", "--worktree", "/pool/1/lighthouse");

        assertThat(exitCode).isZero();
        assertThat(core.received().body()).isEqualTo("{\"branch\":\"feature/LHT-3\",\"worktree\":\"/pool/1/lighthouse\"}");
    }

    @Test
    void recountsTheTaskCostsCountedSinceADay() {
        core.answering(200, "Recounted\n");

        int exitCode = edi("", "task", "recount-costs", "--since", "2026-11-02");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/tasks/recount-costs");
        assertThat(core.received().body()).isEqualTo("{\"since\":\"2026-11-02\"}");
    }

    @Test
    void recordsOneItemOfTheTurnWithItsProjects() {
        core.answering(200, "Recorded. This turn's report holds 1 done item and 0 next items; the stop hands it to you to paste.\n");

        int exitCode = edi("", "turn", "done", "--project", "harness", "--project", "garden", "Fitted", "the \"brass\" lamp");
        StubCore.Request done = core.received();
        edi("", "turn", "next", "Test the lamp");
        StubCore.Request next = core.received();

        assertThat(exitCode).isZero();
        assertThat(out.toString()).startsWith("Recorded. This turn's report holds 1 done item");
        assertThat(done).isEqualTo(new StubCore.Request("POST", "/api/turn", "Bearer stub-token", "text/plain", null, null,
                "{\"done\":[{\"projects\":[\"harness\",\"garden\"],\"text\":\"Fitted the \\\"brass\\\" lamp\"}]}"));
        assertThat(next.body()).isEqualTo("{\"next\":[{\"text\":\"Test the lamp\"}]}");
    }

    @Test
    void recordsTheTurnsItemsFromStandardInputOrAFile() throws IOException {
        core.answering(200, "Recorded.\n");
        String items = "{\"done\":[{\"projects\":[],\"text\":\"Fitted the lamp\"}],\"next\":[],\"tasks\":{}}";
        Path file = Files.writeString(home.resolve("turn.json"), "{\"next\":[{\"text\":\"Fit the lamp\"}]}");

        int exitCode = edi(items, "turn", "record", "-");
        StubCore.Request fromStandardInput = core.received();
        edi("", "turn", "record", file.toString());

        assertThat(exitCode).isZero();
        assertThat(fromStandardInput).isEqualTo(new StubCore.Request("POST", "/api/turn", "Bearer stub-token", "text/plain", null, null, items));
        assertThat(core.received().body()).isEqualTo("{\"next\":[{\"text\":\"Fit the lamp\"}]}");
    }

    @Test
    void entersAPlanFromStandardInputAndShowsItByItsKey() {
        core.answering(201, "Planned HRN-40.\n");
        String plan = "{\"key\":\"HRN-40\",\"slices\":[{\"number\":1,\"title\":\"Fit the lamp\",\"wait\":\"none\"}]}";

        int exitCode = edi(plan, "plan", "add", "--from", "-");
        StubCore.Request added = core.received();
        edi("", "plan", "show", "HRN-40");

        assertThat(exitCode).isZero();
        assertThat(added).isEqualTo(new StubCore.Request("POST", "/api/plans", "Bearer stub-token", "text/plain", null, null, plan));
        assertThat(core.received().target()).isEqualTo("/api/plans/HRN-40");
    }

    @Test
    void asksForTheTurnReportAndReadsTheSitrep() {
        core.answering(200, "report\n");

        edi("", "report");
        StubCore.Request report = core.received();
        edi("", "sitrep", "--json");
        StubCore.Request sitrep = core.received();

        assertThat(report).isEqualTo(new StubCore.Request("POST", "/api/report", "Bearer stub-token", "text/plain", null, null, "{}"));
        assertThat(sitrep).isEqualTo(new StubCore.Request("GET", "/api/report", "Bearer stub-token", "application/json", null, null, ""));
    }

    @Test
    void opensTheConsoleThroughTheCore() {
        core.answering(200, "EDI console: http://127.0.0.1:5490/ is open in your browser.\n");

        int exitCode = edi("", "console");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI console: http://127.0.0.1:5490/ is open in your browser.\n");
        assertThat(core.received()).isEqualTo(new StubCore.Request("POST", "/api/console/open", "Bearer stub-token", "text/plain", null, null, "{}"));
    }

    @Test
    void printsTheSitrepAndThenOpensItsBoardInTheConsole() {
        core.answering(200, "╔══ NEEDS YOUR DECISION ══ nothing open ═══\n").thenAnswering(200, "╔══ NEEDS YOUR DECISION ══ nothing open ═══\n")
                .thenAnswering(200, "EDI console: http://127.0.0.1:5490/ is open in your browser.\n");

        int exitCode = edi("", "sitrep", "--board");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("╔══ NEEDS YOUR DECISION ══ nothing open ═══\nEDI console: http://127.0.0.1:5490/ is open in your browser.\n");
        assertThat(core.requests()).extracting(StubCore.Request::method, StubCore.Request::target)
                .containsExactly(tuple("GET", "/api/report"), tuple("POST", "/api/console/open"));
    }

    @Test
    void opensNoBoardWhenTheSitrepItselfFailed() {
        core.answering(503, "EDI core is starting.\n");

        int exitCode = edi("", "sitrep", "--board");

        assertThat(exitCode).isOne();
        assertThat(core.requests()).extracting(StubCore.Request::target).containsExactly("/api/report");
    }

    @Test
    void armsAResearchBoardByItsAbsolutePathWithItsLinkItsQuestionsAndTheDecisionsItAnswers() {
        core.answering(201, "EDI listens to the research board.\n");

        int exitCode = edi("", "board", "arm", home.resolve("tasks/scout/board.html").toString(), "--task", "scout",
                "--link", "http://127.0.0.1:4387/session/1a2b3c", "--questions", "3", "--bind", "D58,D59");
        int uncounted = edi("", "board", "arm", home.resolve("tasks/scout/board.html").toString(), "--task", "scout",
                "--link", "http://127.0.0.1:4387/session/1a2b3c");

        assertThat(exitCode).isZero();
        assertThat(uncounted).isEqualTo(2);
        assertThat(core.requests()).hasSize(1);
        assertThat(core.received().target()).isEqualTo("/api/boards");
        assertThat(core.received().body()).isEqualTo("{\"file\":\"" + home.resolve("tasks/scout/board.html") + "\",\"task\":\"scout\","
                + "\"link\":\"http://127.0.0.1:4387/session/1a2b3c\",\"questions\":3,\"bind\":[\"D58\",\"D59\"]}");
    }

    @Test
    void armsAResearchBoardCarryingTheDecisionCardItPutsBeforeTheCaptain() {
        core.answering(201, "EDI listens to the research board.\n");
        String card = "{\"question\":\"close-out\",\"title\":\"Close it out?\",\"options\":[{\"key\":\"A\",\"text\":\"Ship the plan\","
                + "\"authorises\":\"promote\"}],\"recommend\":{\"key\":\"A\",\"why\":\"it is ready\"}}";

        int exitCode = edi(card + "\n", "board", "arm", home.resolve("tasks/scout/board.html").toString(), "--task", "scout",
                "--link", "http://127.0.0.1:4387/session/1a2b3c", "--questions", "0", "--card", "-");

        assertThat(exitCode).isZero();
        assertThat(core.received().body()).isEqualTo("{\"file\":\"" + home.resolve("tasks/scout/board.html") + "\",\"task\":\"scout\","
                + "\"link\":\"http://127.0.0.1:4387/session/1a2b3c\",\"questions\":0,\"card\":" + card + "}");
    }

    @Test
    void postsANewRoundOnAListeningBoardByItsAbsolutePath() {
        core.answering(201, "Posted a new round on the research board.\n");

        int exitCode = edi("", "board", "post", home.resolve("tasks/scout/board.html").toString(), "--link", "http://127.0.0.1:4387/session/1a2b3c",
                "--questions", "2");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/boards/rounds");
        assertThat(core.received().body()).isEqualTo("{\"file\":\"" + home.resolve("tasks/scout/board.html")
                + "\",\"link\":\"http://127.0.0.1:4387/session/1a2b3c\",\"questions\":2}");
    }

    @Test
    void showsATasksAnsweredBoardRoundsOrOneOfThem() {
        core.answering(200, "Board rounds answered on scout (1):\n");

        int all = edi("", "board", "show", "scout");
        String allTarget = core.received().target();
        int one = edi("", "board", "show", "scout", "--round", "2");

        assertThat(all).isZero();
        assertThat(one).isZero();
        assertThat(allTarget).isEqualTo("/api/tasks/scout/board");
        assertThat(core.received().target()).isEqualTo("/api/tasks/scout/board?round=2");
    }

    @Test
    void passesOnTheCoresRefusalAndFails() {
        core.answering(409, "fit-lamp is not held.\n");

        int exitCode = edi("", "release", "fit-lamp");

        assertThat(exitCode).isOne();
        assertThat(out.toString()).isEmpty();
        assertThat(err).hasToString("fit-lamp is not held.\n");
    }

    @Test
    void saysWhenTheCoreHasNoToken() throws IOException {
        Files.delete(home.resolve("state/core.token"));

        int exitCode = edi("", "backlog");

        assertThat(exitCode).isOne();
        assertThat(err).hasToString("EDI core is not running: " + home.resolve("state/core.token") + " holds no token.\n");
    }

    @Test
    void saysWhenTheTokenCannotBeRead() throws IOException {
        Path tokenFile = home.resolve("state/core.token");
        Files.delete(tokenFile);
        Files.createDirectory(tokenFile);

        int exitCode = edi("", "backlog");

        assertThat(exitCode).isOne();
        assertThat(err).hasToString("EDI core cannot be reached: " + tokenFile + " cannot be read.\n");
    }

    @Test
    void explainsEverySubcommand() {
        assertThat(edi("", "decision", "answer", "--help")).isZero();

        assertThat(out.toString()).contains("Usage: edi decision answer", "--message=N", "--line=K", "--text=WORDS");
        assertThat(core.received()).isNull();
    }

    @Test
    void asksForASubcommand() {
        assertThat(edi("", "task")).isEqualTo(2);
        assertThat(err.toString()).startsWith("Name a subcommand; edi task --help lists them.");
    }

    @Test
    void readsTheRulesThatFiredInAPeriod() {
        core.answering(200, "Rules that fired since 2026-11-02 09:00 CET: 0.\n");

        int lastWeek = edi("", "rules");
        String lastWeekAsked = core.received().target();
        edi("", "rules", "--days", "30", "--json");

        assertThat(lastWeek).isZero();
        assertThat(List.of(lastWeekAsked, core.received().target())).containsExactly("/api/rules?days=7", "/api/rules?days=30");
    }

    @Test
    void recordsARuleFiredOutsideTheCoreOrKeepsItInTheHomeWhileTheCoreIsDown() throws IOException {
        core.answering(204, "");

        int recorded = edi("", "rules", "fired", "commit.work-key", "--reason", "the subject starts with the work key HRN-3.");
        StubCore.Request told = core.received();
        core.stopAnswering();
        int spooled = edi("", "rules", "fired", "commit.co-author-trailer", "--outcome", "refused", "--reason", "a commit carries no Co-authored-by line.");

        assertThat(List.of(recorded, spooled)).containsOnly(0);
        assertThat(told.target()).isEqualTo("/api/rules/fired");
        assertThat(told.body()).isEqualTo("{\"rule\":\"commit.work-key\",\"outcome\":\"refused\",\"reason\":\"the subject starts with the work key HRN-3.\"}");
        assertThat(Files.readString(home.resolve("state/rules-fired.jsonl"))).startsWith("{\"rule\":\"commit.co-author-trailer\",\"outcome\":\"refused\","
                + "\"reason\":\"a commit carries no Co-authored-by line.\",\"at\":\"").endsWith("\"actor\":\"primary\"}\n");
    }

    private int edi(String input, String... arguments) {
        return new CommandLine(new Edi(Map.of("EDI_HOME", home.toString()), new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }
}
