package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.worker.WorkerTokens;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.Dispatch;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.LaunchKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.SyntheticSecrets;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class ApiTest {

    private static final String TEXT = "text/plain";
    private static final long ABOVE_THE_HIGHEST_LINUX_PID = 4_194_304L;
    private static final WorkerWindow WINDOW_OF_NO_PROCESS = new WorkerWindow("edi-test", ABOVE_THE_HIGHEST_LINUX_PID + 1,
            Instant.parse("2026-10-15T09:00:00Z"), "@1", ABOVE_THE_HIGHEST_LINUX_PID + 2);
    private static final String JSON = "application/json";

    @TempDir
    private static Path home;

    @Autowired
    private WorkerStore workers;

    @Autowired
    private DispatchStore dispatches;

    @Autowired
    private WorkerTokens workerTokens;

    @BeforeAll
    static void prepareTheHome() throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), Intakes.REGISTRY);
    }

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
        properties.add("edi.checkout", () -> home.resolve("checkout").toString());
        properties.add("edi.workers.socket", () -> "edi-test-" + UUID.randomUUID());
    }

    @Test
    void refusesACallerWithoutTheTokenAndKeepsTheTokenOwnerOnly() throws Exception {
        HttpResponse<String> refused = send(HttpRequest.newBuilder(uri("/api/backlog")).GET());

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(refused.body()).isEqualTo("This call needs the EDI token from state/core.token in the EDI home, the token a worker got at launch, "
                + "or a console session opened through edi console.\n");
        assertThat(send(HttpRequest.newBuilder(uri("/api/health")).GET()).statusCode()).isEqualTo(200);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(home.resolve("state/core.token")))).isEqualTo("rw-------");
    }

    @Test
    void refusesAnApiPathWrittenOtherwiseWithoutTheToken() throws Exception {
        assertThat(send(HttpRequest.newBuilder(uri("/%61pi/backlog")).GET()).statusCode()).isEqualTo(401);
        assertThat(send(HttpRequest.newBuilder(uri("//api/backlog")).GET()).statusCode()).isEqualTo(401);
        assertThat(send(HttpRequest.newBuilder(uri("/api/health/liveness")).GET()).statusCode()).isEqualTo(401);
    }

    @Test
    void addsATaskAndShowsItAsTextOrJson() throws Exception {
        HttpResponse<String> added = post("/api/tasks", """
                {"id":"api-lantern","kind":"ship","projects":["harness"],"title":"Rework the lantern","key":"HRN-3","base":"develop"}""", TEXT);

        assertThat(added.statusCode()).isEqualTo(201);
        assertThat(added.body()).isEqualTo("Queued api-lantern on HRN-3.\n");
        assertThat(get("/api/tasks/api-lantern", TEXT).body()).startsWith("api-lantern  ship  queued\nRework the lantern\nProjects: harness\nWork key: HRN-3\nBase: develop\n");
        assertThat(get("/api/tasks/api-lantern", JSON).body()).contains(
                "\"id\":\"api-lantern\"", "\"kind\":\"ship\"", "\"state\":\"queued\"", "\"projects\":[\"harness\"]", "\"workKey\":\"HRN-3\"", "\"base\":\"develop\"");
    }

    @Test
    void raisesAndAnswersADecisionThroughItsNumber() throws Exception {
        post("/api/tasks", "{\"id\":\"api-lamp\",\"kind\":\"captain\",\"title\":\"Choose the lamp\"}", TEXT);

        HttpResponse<String> raised = post("/api/tasks/api-lamp/decision", """
                {"title":"Which lamp?","context":"","options":[{"key":"A","text":"LED"},{"key":"B","text":"Oil"}],"recommend":{"key":"A","why":"it lasts"}}""", TEXT);
        String number = raised.body().strip();
        String heldAsJson = get("/api/tasks/" + number, JSON).body();
        UUID primary = UUID.randomUUID();
        primaryHook(primary, "session-start", "{}");
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"" + number + " A\"}");
        HttpResponse<String> untyped = answerInSession(primary, number, "{\"option\":\"A\",\"text\":\"" + number + " B\"}");
        HttpResponse<String> answered = answerInSession(primary, number, "{\"option\":\"A\",\"text\":\"" + number + " A\"}");
        primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertRefusal(untyped, 409, "The captain did not type \"" + number + " B\" in this session; record their words exactly as typed,"
                + " the whole message or one whole line of it.");
        assertThat(raised.statusCode()).isEqualTo(201);
        assertThat(number).matches("D\\d+");
        assertThat(heldAsJson).contains("\"kind\":\"decision\"", "\"until\":\"captain's word\"");
        assertThat(answered.body()).isEqualTo("Recorded the answer to " + number + " (option A); api-lamp is done.\n");
        assertThat(get("/api/decisions?open=true", TEXT).body()).doesNotContain(number + "  api-lamp");
        assertThat(get("/api/decisions", JSON).body()).contains("\"verbatim\":\"" + number + " A\"", "\"channel\":\"chat\"");
    }

    @Test
    void answersEveryRefusalInOnePlainSentence() throws Exception {
        post("/api/tasks", "{\"id\":\"api-horn\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Tune the horn\"}", TEXT);

        assertRefusal(get("/api/tasks/api-nowhere", JSON), 404, "No task is called api-nowhere.");
        assertRefusal(post("/api/tasks/api-horn/release", "{}", TEXT), 409, "api-horn is not held.");
        assertRefusal(post("/api/tasks", "{\"kind\":", TEXT), 400, "The request body is not the JSON this call expects.");
        assertRefusal(post("/api/tasks", "{\"kind\":\"ship\",\"projects\":[\"harness\"]}", TEXT), 400, "The call needs its title.");
        assertRefusal(get("/api/tasks?state=flying", TEXT), 400, "A task state is queued, in-flight, done or dropped, not flying.");
        assertRefusal(post("/api/tasks/api-horn/hold", "{\"kind\":\"deferral\",\"reason\":\"later\",\"until\":\"soon\"}", TEXT),
                400, "A hold lasts until a date such as 2026-11-01, not soon.");
        assertRefusal(get("/api/migrations/firstofficer/plan?from=relative", TEXT), 400, "Name the old home by its absolute path, not relative.");
    }

    @Test
    void listsTheBacklogAndTheProjects() throws Exception {
        post("/api/tasks", "{\"id\":\"api-flag\",\"kind\":\"ship\",\"projects\":[\"garden\"],\"title\":\"Raise the flag\"}", TEXT);

        assertThat(get("/api/backlog", TEXT).body()).contains("api-flag", "Raise the flag");
        assertThat(get("/api/projects", TEXT).body()).isEqualTo("Projects (2)\n  harness  work keys HRN-<n>\n  garden\n");
    }

    @Test
    void rendersTheSitrepForTheCallersPaneAndTheTurnReportWithOnlyWhatNeedsTheCaptain() throws Exception {
        post("/api/tasks", "{\"id\":\"api-buoy\",\"kind\":\"captain\",\"title\":\"Choose the buoy\"}", TEXT);
        String number = post("/api/tasks/api-buoy/decision", """
                {"title":"Which buoy colour?","context":"","options":[{"key":"A","text":"Red"}],"recommend":{"key":"A","why":"it shows"}}""", TEXT)
                .body().strip();
        HttpResponse<String> sitrep = get("/api/report?paneWidth=90", TEXT);
        HttpResponse<String> report = post("/api/report", """
                {"done":[{"projects":["harness"],"text":"Painted the buoy"}],"next":[],"tasks":{}}""", TEXT);
        HttpResponse<String> asJson = get("/api/report", JSON);

        assertThat(sitrep.statusCode()).isEqualTo(200);
        assertThat(sitrep.body().lines().findFirst().orElseThrow()).startsWith("╔══ NEEDS YOUR DECISION ══ ").hasSize(88);
        assertThat(sitrep.body()).contains("\nDONE THIS TURN  (0)\n", "  Nothing finished this turn\n", "\nONGOING  (");
        assertThat(report.statusCode()).isEqualTo(200);
        assertThat(report.body().lines().findFirst().orElseThrow()).startsWith("╔══ NEEDS YOUR DECISION ══ ").hasSize(120);
        assertThat(report.body()).contains("║  %-6sWhich buoy colour?\n".formatted(number), "║        » Recommended: A - it shows\n",
                "\nDONE THIS TURN  (1)\n───────────────────\n  harness           • Painted the buoy\n")
                .doesNotContain("ONGOING", "PARKED", "BACKLOG", "NEXT", "Nothing finished this turn");
        assertThat(asJson.body()).contains("\"fleet\":{", "\"turn\":{\"done\":[]", "\"width\":{\"columns\":120}");
    }

    @Test
    void givesItsWorkersAWindowThatNoProcessOfTheMachineCanBe() {
        assertThat(ProcessHandle.of(WINDOW_OF_NO_PROCESS.serverPid())).isEmpty();
        assertThat(ProcessHandle.of(WINDOW_OF_NO_PROCESS.panePid())).isEmpty();
    }

    @Test
    void carriesTheTurnsItemsIntoTheSitrepWhenTheTurnIsPosted() throws Exception {
        HttpResponse<String> sitrep = post("/api/report/sitrep", """
                {"done":[{"projects":["harness"],"text":"Moored the buoy"}],"next":[],"tasks":{}}""", TEXT);

        assertThat(sitrep.statusCode()).isEqualTo(200);
        assertThat(sitrep.body().lines().findFirst().orElseThrow()).startsWith("╔══ NEEDS YOUR DECISION ══ ").hasSize(120);
        assertThat(sitrep.body()).contains("\nDONE THIS TURN  (1)\n───────────────────\n  harness           • Moored the buoy\n", "\nONGOING  (")
                .doesNotContain("Nothing finished this turn");
    }

    @Test
    void drawsADecisionCardInFullInTheTurnItIsRaisedAndOnOneLineInTheTurnsAfter() throws Exception {
        UUID primary = UUID.randomUUID();
        primaryHook(primary, "session-start", "{}");
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"Ask me about the bell.\"}");
        post("/api/tasks", "{\"id\":\"api-bell\",\"kind\":\"captain\",\"title\":\"Choose the bell\"}", TEXT);
        String number = post("/api/tasks/api-bell/decision", """
                {"title":"Which bell?","context":"","options":[{"key":"A","text":"Brass"},{"key":"B","text":"Iron"}],"recommend":{"key":"A","why":"it rings"}}""",
                TEXT).body().strip();
        String raisedTurn = turnReportInSession(primary).body();
        String raisedTurnAgain = turnReportInSession(primary).body();
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"Carry on.\"}");
        String laterTurn = turnReportInSession(primary).body();
        primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertThat(raisedTurn).contains("║  %-6sWhich bell?\n".formatted(number), "║        (A)  Brass\n", "║        » Recommended: A - it rings\n");
        assertThat(raisedTurnAgain).isEqualTo(raisedTurn);
        assertThat(laterTurn).contains("║  %-6sWhich bell? » Recommended: A - Brass\n".formatted(number)).doesNotContain("(A)  Brass", "it rings");
    }

    @Test
    void refusesAMalformedTurnOrParameterInOnePlainSentence() throws Exception {
        assertRefusal(post("/api/report", "{\"done\":[{\"text\":\"x\"}],\"extra\":1}", TEXT), 400, "The turn takes only done, next and tasks, not extra.");
        assertRefusal(post("/api/report", "[]", TEXT), 400, "The turn is one JSON object with done, next and tasks.");
        assertRefusal(get("/api/report?paneWidth=wide", TEXT), 400, "The call's paneWidth parameter cannot be wide.");
        assertRefusal(get("/api/backlog?ready=maybe", TEXT), 400, "The call's ready parameter cannot be maybe.");
    }

    @Test
    void checksTheOutgoingCommitsOfARepositoryBeforeAPush() throws Exception {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
        FixtureRepository repository = FixtureRepository.initialise(Files.createDirectories(home.resolve("worktrees/harness/api-secrets")))
                .write("README.md", "A project.\n")
                .commit("Start the project")
                .branch("secrets")
                .write("config/app.properties", "cloud.key=" + SyntheticSecrets.awsAccessKeyId() + "\n")
                .commit("Add service settings");

        HttpResponse<String> clear = post("/api/publish-checks", "{\"repository\":\"" + repository.root() + "\",\"range\":\"main..main\"}", TEXT);
        HttpResponse<String> refused = post("/api/publish-checks", "{\"repository\":\"" + repository.root() + "\",\"range\":\"main..secrets\"}", TEXT);

        assertThat(clear.statusCode()).isEqualTo(200);
        assertThat(clear.body()).isEqualTo("Clear: no secret, no personal material and no -SNAPSHOT version in main..main\n");
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).isEqualTo("config/app.properties:1 in commit " + repository.head().substring(0, 7) + ": gitleaks rule aws-access-token\n"
                + "Refused: the outgoing commits carry a secret, personal material or a -SNAPSHOT version.\n");
    }

    @Test
    void checksOnlyARegisteredClonesOrAnIsolatedCopysCommits() throws Exception {
        Path elsewhere = FixtureRepository.initialise(Files.createTempDirectory(home, "elsewhere-")).write("README.md", "A stranger.\n")
                .commit("Start elsewhere").root();
        Path linkedCopy = Files.createSymbolicLink(Files.createDirectories(home.resolve("worktrees/harness")).resolve("api-linked"), elsewhere);

        for (Path repository : List.of(elsewhere, linkedCopy, home.resolve("worktrees/harness/api-linked/../../../" + elsewhere.getFileName()))) {
            assertRefusal(post("/api/publish-checks", "{\"repository\":\"" + repository + "\",\"range\":\"main..main\"}", TEXT), 409,
                    "The publish check runs only in a registered project's clone or a task's isolated copy under " + home.resolve("worktrees")
                            + ", and " + repository.normalize() + " is neither.");
        }
    }

    @Test
    void letsAWorkerCheckOnlyItsOwnIsolatedCopy() throws Exception {
        String worker = workerTokenFor("api-pusher");
        Path ownCopy = FixtureRepository.initialise(Files.createDirectories(home.resolve("worktrees/harness/api-pusher"))).write("README.md", "Lamp.\n")
                .commit("Fit the lamp").root();
        Path otherCopy = FixtureRepository.initialise(Files.createDirectories(home.resolve("worktrees/harness/api-other"))).write("README.md", "Horn.\n")
                .commit("Tune the horn").root();

        HttpResponse<String> own = send(as(worker, "/api/publish-checks", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"repository\":\"" + ownCopy + "\",\"range\":\"main..main\"}")));
        HttpResponse<String> other = send(as(worker, "/api/publish-checks", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"repository\":\"" + otherCopy + "\",\"range\":\"main..main\"}")));

        assertThat(own.body()).doesNotContain("runs only in");
        assertRefusal(other, 409, "A worker runs the publish check only in its own isolated copy, " + home.resolve("worktrees/harness/api-pusher")
                + ", and " + otherCopy + " is not it.");
    }

    @Test
    void plansTheMigrationOfAnOldHome() throws Exception {
        HttpResponse<String> plan = get("/api/migrations/firstofficer/plan?from=" + Path.of("src/test/resources/fork-home").toAbsolutePath(), TEXT);

        assertThat(plan.statusCode()).isEqualTo(200);
        assertThat(plan.body()).contains("Tasks to import (17)", "the first new decision is D58");
    }

    @Test
    void tellsAWorkerFromThePrimaryByItsTokenAndKeepsEachToItsCommands() throws Exception {
        String worker = workerTokenFor("api-worker");

        HttpResponse<String> status = send(as(worker, "/api/status", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"state\":\"working\",\"text\":\"Fitting the lamp.\"}")));

        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(status.body()).isEqualTo("Recorded working for api-worker#1.\n");
        assertRefusal(send(as(worker, "/api/tasks", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Sneak in\"}"))),
                403, "edi task add is the primary's to run; a worker reports through edi status and reads edi inbox.");
        assertRefusal(post("/api/status", "{\"state\":\"working\",\"text\":\"x\"}", TEXT),
                403, "edi status is a worker's command: it runs under the token a worker gets at launch.");
        assertThat(send(as("not-a-token", "/api/inbox", TEXT).GET()).statusCode()).isEqualTo(401);
        assertThat(send(as(worker, "/api/inbox", TEXT).GET()).body()).isEqualTo("No message waits for you.\n");
        assertThat(get("/api/tasks/api-worker", TEXT).body()).contains("worker:api-worker#1");
    }

    @Test
    void keepsDeliveryToThePrimaryAndAnswersItsRefusalsInPlainSentences() throws Exception {
        String worker = workerTokenFor("api-deliver");
        post("/api/tasks", "{\"id\":\"api-undispatched\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Paint the buoy\"}", TEXT);

        for (String command : List.of("deliver", "cleanup")) {
            assertRefusal(send(as(worker, "/api/tasks/api-deliver/" + command, TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString("{}"))),
                    403, "edi " + command + " is the primary's to run; a worker reports through edi status and reads edi inbox.");
        }
        assertRefusal(send(as(worker, "/api/merged", TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString("{\"reference\":\"api-deliver\"}"))),
                403, "edi merged is the primary's to run; a worker reports through edi status and reads edi inbox.");
        assertRefusal(post("/api/tasks/api-undispatched/cleanup", "{}", TEXT), 409, "api-undispatched was never dispatched.");
        assertRefusal(post("/api/tasks/api-nowhere/deliver", "{}", TEXT), 404, "No task is called api-nowhere.");
        assertRefusal(post("/api/merged", "{}", TEXT), 400, "The call needs its reference.");
        assertRefusal(post("/api/merged", "{\"reference\":\"https://github.com/example-owner/harness/pull/9\"}", TEXT), 409,
                "Nothing was delivered at https://github.com/example-owner/harness/pull/9.");
    }

    @Test
    void answersClaudeCodesHooksInTheJsonClaudeCodeActsOn() throws Exception {
        String worker = workerTokenFor("api-hooks");

        HttpResponse<String> denied = hook(worker, "pre-tool-use", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"pkill -f java\"},\"cwd\":\"/tmp\"}");
        HttpResponse<String> allowed = hook(worker, "pre-tool-use", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"},\"cwd\":\"/tmp\"}");
        HttpResponse<String> stopped = hook(worker, "stop", "{\"hook_event_name\":\"Stop\"}");
        HttpResponse<String> searched = hook(worker, "pre-tool-use",
                "{\"tool_name\":\"Glob\",\"tool_input\":{\"pattern\":\"core.tok*\",\"path\":\"" + home.resolve("state") + "\"},\"cwd\":\"/tmp\"}");

        assertThat(denied.statusCode()).isEqualTo(200);
        assertThat(denied.body()).startsWith("{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\",\"permissionDecision\":\"deny\","
                + "\"permissionDecisionReason\":\"The guard refuses this: pkill signals every process");
        assertThat(allowed.statusCode()).isEqualTo(204);
        assertThat(stopped.statusCode()).isEqualTo(204);
        assertThat(searched.body()).contains("\"permissionDecision\":\"deny\"", "this file search touches a secret");
        assertRefusal(hook(worker, "teleport", "{}"), 400, "The hooks answered are session-start, user-prompt-submit, pre-tool-use, stop, stop-failure, notification and session-end, not teleport.");
        assertThat(send(authorised("/api/hooks/pre-tool-use", JSON).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"tmux kill-server\"}}"))).body())
                .contains("\"permissionDecision\":\"deny\"", "name the socket with -L or -S");
    }

    @Test
    void movesThePrimaryRoleToTheSessionTheCaptainTypedInAndRefusesTheStandbysChanges() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        HttpResponse<String> started = primaryHook(first, "session-start", "{\"source\":\"startup\"}");
        primaryHook(second, "session-start", "{\"source\":\"startup\"}");
        primaryHook(second, "user-prompt-submit", "{\"prompt\":\"Queue the anchor.\"}");
        HttpResponse<String> refused = send(inSession(first, "/api/tasks", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"id\":\"api-anchor\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Weigh the anchor\"}")));
        HttpResponse<String> queued = send(inSession(second, "/api/tasks", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"id\":\"api-anchor\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Weigh the anchor\"}")));
        HttpResponse<String> unreported = primaryHook(second, "stop", "{\"stop_hook_active\":false,\"background_tasks\":[]}");
        send(inSession(second, "/api/report", TEXT).GET());
        HttpResponse<String> reported = primaryHook(second, "stop", "{\"stop_hook_active\":false,\"background_tasks\":"
                + "[{\"id\":\"b1\",\"type\":\"shell\",\"status\":\"running\",\"command\":\"edi wait\"}]}");
        primaryHook(first, "session-end", "{\"reason\":\"prompt_input_exit\"}");
        primaryHook(second, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertThat(started.statusCode()).isEqualTo(200);
        assertThat(new JsonMapper().readTree(started.body()).path("hookSpecificOutput").path("additionalContext").asString())
                .startsWith("EDI session-start digest\nThis session is the primary")
                .contains("\nPersonality: ", "\nAddress word \"Commander\": ", "\nBad news: humour 0, ")
                .contains("\nThe console's sitrep page is open in the captain's browser at " + uri("/") + ".\n");
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(refused.body()).startsWith("This session is standby: the captain typed last in another EDI session");
        assertThat(queued.statusCode()).isEqualTo(201);
        assertThat(unreported.body()).isEqualTo("{\"decision\":\"block\",\"reason\":\"This turn changed state: render the turn report with "
                + "edi report --turn - and paste it verbatim, then stop.\"}");
        assertThat(reported.statusCode()).isEqualTo(204);
    }

    @Test
    void tellsAnyCallerWhetherThePrimarysLastTurnEndedWithAnEdiWait() throws Exception {
        UUID primary = UUID.randomUUID();
        primaryHook(primary, "session-start", "{\"source\":\"startup\"}");
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"Watch the lamp.\"}");
        HttpResponse<String> inTheTurn = get("/api/primary/wait", TEXT);
        primaryHook(primary, "stop", "{\"stop_hook_active\":false,\"background_tasks\":"
                + "[{\"id\":\"b1\",\"type\":\"shell\",\"status\":\"running\",\"command\":\"edi wait\"}]}");

        HttpResponse<String> live = get("/api/primary/wait", TEXT);
        HttpResponse<String> forAWorker = send(as(workerTokenFor("api-primary-wait"), "/api/primary/wait", JSON).GET());
        primaryHook(primary, "session-start", "{\"source\":\"compact\"}");
        HttpResponse<String> compacted = get("/api/primary/wait", TEXT);
        primaryHook(primary, "session-start", "{\"source\":\"resume\"}");
        HttpResponse<String> resumed = get("/api/primary/wait", TEXT);
        primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertThat(inTheTurn.body()).startsWith("The primary is in a turn since ").endsWith("; its stop checks that edi wait is live.\n");
        assertThat(live.statusCode()).isEqualTo(200);
        assertThat(live.body()).startsWith("edi wait is live: it was running when the primary's last turn ended, at ");
        assertThat(forAWorker.statusCode()).isEqualTo(200);
        assertThat(new JsonMapper().readTree(forAWorker.body()).path("sighting").asString()).isEqualTo("LIVE");
        assertThat(compacted.body()).isEqualTo(live.body());
        assertThat(resumed.body()).startsWith("No edi wait is live").contains(": the primary's last turn ended at ");
    }

    @Test
    void wakesThePrimaryWithWhatNeedsItAndListsTheOutageHoldList() throws Exception {
        String worker = workerTokenFor("api-wake");
        UUID primary = UUID.randomUUID();
        primaryHook(primary, "session-start", "{\"source\":\"startup\"}");
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"Watch the lamp.\"}");
        send(as(worker, "/api/status", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"state\":\"needs-decision\",\"key\":\"lamp\",\"text\":\"Brass or oil?\"}")));

        HttpResponse<String> woken = send(waitIn(primary));
        HttpResponse<String> sessionless = post("/api/wait", "{}", TEXT);
        primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertThat(woken.statusCode()).isEqualTo(200);
        assertThat(woken.body()).startsWith("EDI wake: ").contains("api-wake#1 needs a decision: (lamp) Brass or oil?",
                "Handle these, then start edi wait in the background again.");
        assertRefusal(sessionless, 409, "edi wait runs inside the primary's Claude Code session, which names itself in CLAUDE_CODE_SESSION_ID.");
        assertThat(get("/api/recovery", TEXT).body()).startsWith("The outage hold list is empty.\nRecovery so far: 0 relaunches counted");
    }

    @Test
    void changesThePresenceAndRefusesAnAwayMandateTheCaptainDidNotType() throws Exception {
        UUID primary = UUID.randomUUID();
        primaryHook(primary, "session-start", "{}");
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"/afk Carry on with the lamp.\"}");

        HttpResponse<String> quiet = post("/api/presence", "{\"mode\":\"quiet\"}", TEXT);
        HttpResponse<String> forged = send(inSession(primary, "/api/presence", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"mode\":\"away\",\"mandate\":\"Merge everything.\"}")));
        HttpResponse<String> away = send(inSession(primary, "/api/presence", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"mode\":\"away\",\"mandate\":\"Carry on with the lamp.\"}")));
        HttpResponse<String> shown = get("/api/presence", JSON);
        HttpResponse<String> present = post("/api/presence", "{\"mode\":\"present\"}", TEXT);
        primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertThat(quiet.body()).startsWith("Presence: quiet since ").endsWith(", on request.\n");
        assertRefusal(forged, 409, "The captain did not type \"Merge everything.\" in this session; record their words exactly as typed,"
                + " the whole message or one whole line of it.");
        assertThat(away.body()).contains("away since ", "on the words \"Carry on with the lamp.\"");
        assertThat(shown.body()).contains("\"mode\":\"away\"", "\"cause\":\"captain\"", "\"mandate\":\"Carry on with the lamp.\"", "\"workerCap\":");
        assertThat(present.body()).isEqualTo("Presence: present.\n");
    }

    @Test
    void showsAndKeepsThePersonalityTheCaptainChooses() throws Exception {
        String worker = workerTokenFor("api-personality");

        HttpResponse<String> changed = post("/api/personality", "{\"change\":[\"humour\",\"2\",\"formality\",\"10\"]}", TEXT);
        HttpResponse<String> shown = get("/api/personality", TEXT);
        HttpResponse<String> unknown = post("/api/personality", "{\"change\":[\"humour\",\"8\",\"for\",\"now\"]}", TEXT);
        HttpResponse<String> byAWorker = send(as(worker, "/api/personality", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"change\":[\"plain\"]}")));

        assertThat(changed.body()).startsWith("Personality: humour 2, formality 10, verbosity 5,").contains("\nAddress word \"Commander\": almost always");
        assertThat(shown.body()).isEqualTo(changed.body());
        assertThat(Files.readString(home.resolve("config/personal.yaml"))).contains("edi-personality:\n  humour: 2\n  formality: 10\n");
        assertRefusal(unknown, 400, "for is not a personality scale; the scales are humour, formality, verbosity, warmth, directness, initiative, depth,"
                + " enthusiasm, flavour.");
        assertThat(byAWorker.statusCode()).isEqualTo(403);
    }

    @Test
    void checksTheStartupMemoryAndFailsOnARepeatedRuleHeading() throws Exception {
        Path profile = home.resolve("memory/profile.md");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "# Profile\n\n## Commits\n\n- One rule. (2026-10-15)\n\n## Commits\n\n- Again. (2026-10-15)\n");

        HttpResponse<String> failing = get("/api/memory/check", TEXT);
        HttpResponse<String> stowList = get("/api/memory/stow-list", TEXT);
        Files.delete(profile);
        HttpResponse<String> passing = get("/api/memory/check", TEXT);

        assertThat(failing.statusCode()).isEqualTo(409);
        assertThat(failing.body()).contains("The rule heading \"commits\" appears more than once; keep one.", "The memory check fails");
        assertThat(stowList.body()).contains("To curate (1)", "commits - this rule heading appears more than once");
        assertThat(passing.statusCode()).isEqualTo(200);
        assertThat(passing.body()).contains("The memory check passes.");
    }

    @Test
    void preparesThePrimarySessionForEdiUp() throws Exception {
        HttpResponse<String> launch = post("/api/primary/launch", "{}", TEXT);

        assertThat(launch.statusCode()).isEqualTo(200);
        assertThat(launch.body()).matches("(?s)session: edi-\\d{6}-\\d{4}\ndirectory: .*\nsettings: .*settings\\.json\nsystem prompt: .*system-prompt\\.md\n");
        assertThat(Files.readString(home.resolve("state/primary/system-prompt.md"))).startsWith("# EDI: the primary's anchor");
    }

    @Test
    void writesThePrimarysStatusLineWithTheBranchOfItsWorkingDirectory(@TempDir Path project) throws Exception {
        FixtureRepository.initialise(project).write("lamp.txt", "brass\n").commit("Fit the lamp").write("draft.txt", "not yet added\n");
        String payload = statusLinePayload().replace("/home/example/harness", project.toString());

        HttpResponse<String> line = post("/api/statusline", payload, TEXT);
        HttpResponse<String> forAWorker = send(as(workerTokenFor("api-statusline"), "/api/statusline", TEXT).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString(payload)));

        assertThat(line.statusCode()).isEqualTo(200);
        assertThat(line.body().replaceAll("\u001b\\[[0-9;]*m", ""))
                .startsWith("Opus 5.5 · medium -- Context: 6% -- Session tokens: 58k (C 25k/!C 33k) -- 5-hour: 28% (")
                .endsWith(") -- Branch: main* -- Cache: 43%\n");
        assertRefusal(forAWorker, 403, "edi statusline runs under the EDI home's own token, in the captain's terminal or the primary session.");
    }

    @Test
    void refusesADispatchAboveHighUntilTheCaptainsWordOnTheTaskIsRecordedForItAndWithoutAReason() throws Exception {
        post("/api/tasks", "{\"id\":\"api-beacon\",\"kind\":\"scout\",\"projects\":[\"harness\"],\"title\":\"Map the beacon\",\"key\":\"HRN-8\"}", TEXT);
        UUID primary = UUID.randomUUID();
        primaryHook(primary, "session-start", "{}");
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"Map the beacon at xhigh.\"}");

        HttpResponse<String> aGeneralWord = captainSaidInSession(primary, "{\"text\":\"Map the beacon at xhigh.\",\"task\":\"api-beacon\"}");
        HttpResponse<String> withoutTheWordForIt = dispatchInSession(primary, "api-beacon", "xhigh", "the captain asked for xhigh");
        HttpResponse<String> withoutAReason = dispatchInSession(primary, "api-beacon", "max", null);
        HttpResponse<String> beyondTheHarness = dispatchInSession(primary, "api-beacon", "ultra", "the captain asked for ultra");
        HttpResponse<String> forAnotherAct = captainSaidInSession(primary, "{\"text\":\"Map the beacon at xhigh.\",\"task\":\"api-beacon\",\"for\":\"drop\"}");
        HttpResponse<String> forTheHome = captainSaidInSession(primary, "{\"text\":\"Map the beacon at xhigh.\",\"for\":\"effort-above-high\"}");
        primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

        assertThat(aGeneralWord.statusCode()).isEqualTo(200);
        assertRefusal(withoutTheWordForIt, 409, "To run one task above high, the effort ceiling, you need the captain's word on api-beacon in this session"
                + " recorded for it: record it with edi captain said --task api-beacon --for effort-above-high --text \"<their words>\".");
        assertRefusal(forAnotherAct, 400, "The captain's word is recorded for effort-above-high alone, not drop.");
        assertRefusal(forTheHome, 400, "The captain's word for effort-above-high is on one task.");
        assertRefusal(withoutAReason, 400, "An override of the dispatch rules records the captain's reason for it.");
        assertRefusal(beyondTheHarness, 400, "Effort is low, medium or high, and high is the ceiling, so not ultra;"
                + " only the captain's word for one task lifts that task to xhigh or max.");
    }

    private static HttpResponse<String> captainSaidInSession(UUID session, String said) throws Exception {
        return send(inSession(session, "/api/captain/said", TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString(said)));
    }

    private static HttpResponse<String> dispatchInSession(UUID session, String task, String effort, String reason) throws Exception {
        String body = "{\"category\":\"research\",\"overrideModel\":\"claude-opus-5-5\",\"overrideEffort\":\"" + effort + "\""
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
        return send(inSession(session, "/api/tasks/" + task + "/dispatch", TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString(body)));
    }

    private static HttpResponse<String> answerInSession(UUID session, String number, String answer) throws Exception {
        return send(inSession(session, "/api/decisions/" + number + "/answer", TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString(answer)));
    }

    private static HttpResponse<String> turnReportInSession(UUID session) throws Exception {
        return send(inSession(session, "/api/report", TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString("{}")));
    }

    private static HttpRequest.Builder waitIn(UUID session) throws IOException {
        return inSession(session, "/api/wait", TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString("{}"));
    }

    private static HttpResponse<String> primaryHook(UUID session, String event, String fields) throws Exception {
        String payload = "{\"session_id\":\"" + session + "\"," + fields.substring(1);
        return send(inSession(session, "/api/hooks/" + event, JSON).header("Content-Type", JSON).POST(BodyPublishers.ofString(payload)));
    }

    private static HttpRequest.Builder inSession(UUID session, String path, String accepted) throws IOException {
        return authorised(path, accepted).header("EDI-Session", session.toString());
    }

    private String workerTokenFor(String task) throws Exception {
        post("/api/tasks", "{\"id\":\"" + task + "\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Fit the lamp\",\"key\":\"HRN-3\",\"base\":\"develop\"}", TEXT);
        TaskId id = new TaskId(task);
        dispatches.save(Dispatch.requested(id, CategoryId.IMPLEMENTATION, BranchName.forWork(BranchKind.FEATURE, WorkKey.parse("HRN-3")), "", null, null,
                Instant.parse("2026-10-15T09:00:00Z")));
        WorkerTokens.IssuedToken token = workerTokens.issueInto(home.resolve("state/workers/" + task));
        workers.insert(Worker.launched(WorkerId.first(id), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                WINDOW_OF_NO_PROCESS,
                new WorkerLaunch(LaunchKind.DISPATCH, Instant.parse("2026-10-15T09:00:00Z"), null)), token.digest());
        return Files.readString(token.file());
    }

    private static String statusLinePayload() throws IOException {
        try (InputStream fixture = ApiTest.class.getResourceAsStream("/statusline/first-answer.json")) {
            return new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static HttpResponse<String> hook(String token, String event, String payload) throws Exception {
        return send(as(token, "/api/hooks/" + event, JSON).header("Content-Type", JSON).POST(BodyPublishers.ofString(payload)));
    }

    private static HttpRequest.Builder as(String token, String path, String accepted) throws IOException {
        return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token).header("Accept", accepted);
    }

    private static void assertRefusal(HttpResponse<String> response, int status, String sentence) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).isEqualTo(sentence + "\n");
    }

    private static HttpResponse<String> get(String path, String accepted) throws Exception {
        return send(authorised(path, accepted).GET());
    }

    private static HttpResponse<String> post(String path, String json, String accepted) throws Exception {
        return send(authorised(path, accepted).header("Content-Type", JSON).POST(BodyPublishers.ofString(json)));
    }

    private static HttpRequest.Builder authorised(String path, String accepted) throws IOException {
        return HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + Files.readString(home.resolve("state/core.token")))
                .header("Accept", accepted);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request.build(), BodyHandlers.ofString());
        }
    }

    private static URI uri(String path) throws IOException {
        return URI.create("http://127.0.0.1:" + Files.readString(home.resolve("state/core.port")).strip() + path);
    }
}
