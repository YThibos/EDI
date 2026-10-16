package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApiTest {

    private static final Path HOME = registeredHome();
    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";

    @Autowired
    private WorkerStore workers;

    @Autowired
    private DispatchStore dispatches;

    @Autowired
    private WorkerTokens workerTokens;

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", HOME::toString);
        properties.add("edi.workers.socket", () -> "edi-test-" + UUID.randomUUID());
    }

    @AfterAll
    static void removeTheTemporaryHome() throws IOException {
        try (Stream<Path> files = Files.walk(HOME)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    void refusesACallerWithoutTheTokenAndKeepsTheTokenOwnerOnly() throws Exception {
        HttpResponse<String> refused = send(HttpRequest.newBuilder(uri("/api/backlog")).GET());

        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(refused.body()).isEqualTo("This call needs the EDI token from state/core.token in the EDI home, or the token EDI gave a worker at launch.\n");
        assertThat(send(HttpRequest.newBuilder(uri("/api/health")).GET()).statusCode()).isEqualTo(200);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(HOME.resolve("state/core.token")))).isEqualTo("rw-------");
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
        assertThat(added.body()).isEqualTo("Queued api-lantern.\n");
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
        HttpResponse<String> answered = post("/api/decisions/" + number + "/answer", "{\"option\":\"A\",\"text\":\"" + number + " A\"}", TEXT);

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
    void rendersTheCaptainReportForTheCallersPaneWithOrWithoutATurn() throws Exception {
        HttpResponse<String> sitrep = get("/api/report?paneWidth=90", TEXT);
        HttpResponse<String> report = post("/api/report", """
                {"done":[{"projects":["harness"],"text":"Painted the buoy"}],"next":[],"tasks":{}}""", TEXT);
        HttpResponse<String> asJson = get("/api/report", JSON);

        assertThat(sitrep.statusCode()).isEqualTo(200);
        assertThat(sitrep.body().lines().findFirst().orElseThrow()).startsWith("╔══ NEEDS YOUR DECISION ══ ").hasSize(88);
        assertThat(sitrep.body()).contains("\nDONE THIS TURN  (0)\n", "  Nothing finished this turn\n");
        assertThat(report.statusCode()).isEqualTo(200);
        assertThat(report.body().lines().findFirst().orElseThrow()).hasSize(120);
        assertThat(report.body()).contains("\nDONE THIS TURN  (1)\n───────────────────\n  harness           • Painted the buoy\n");
        assertThat(asJson.body()).contains("\"fleet\":{", "\"turn\":{\"done\":[]", "\"width\":{\"columns\":120}");
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
        FixtureRepository repository = FixtureRepository.initialise(Files.createTempDirectory(HOME, "repository-"))
                .write("README.md", "A project.\n")
                .commit("Start the project")
                .branch("secrets")
                .write("config/app.properties", "cloud.key=" + SyntheticSecrets.awsAccessKeyId() + "\n")
                .commit("Add service settings");

        HttpResponse<String> clear = post("/api/publish-checks", "{\"repository\":\"" + repository.root() + "\",\"range\":\"main..main\"}", TEXT);
        HttpResponse<String> refused = post("/api/publish-checks", "{\"repository\":\"" + repository.root() + "\",\"range\":\"main..secrets\"}", TEXT);

        assertThat(clear.statusCode()).isEqualTo(200);
        assertThat(clear.body()).isEqualTo("Clear: no secret and no personal material in main..main\n");
        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).isEqualTo("config/app.properties:1 in commit " + repository.head().substring(0, 7) + ": gitleaks rule aws-access-token\n"
                + "Refused: the outgoing commits carry a secret or personal material.\n");
    }

    @Test
    void plansTheMigrationOfAnOldHome() throws Exception {
        HttpResponse<String> plan = get("/api/migrations/firstofficer/plan?from=" + Path.of("src/test/resources/fork-home").toAbsolutePath(), TEXT);

        assertThat(plan.statusCode()).isEqualTo(200);
        assertThat(plan.body()).contains("Tasks to import (15)", "the first new decision is D58");
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
                403, "edi status is a worker's command: it runs under the token EDI gives a worker at launch.");
        assertThat(send(as("not-a-token", "/api/inbox", TEXT).GET()).statusCode()).isEqualTo(401);
        assertThat(send(as(worker, "/api/inbox", TEXT).GET()).body()).isEqualTo("No message waits for you.\n");
        assertThat(get("/api/tasks/api-worker", TEXT).body()).contains("worker:api-worker#1");
    }

    @Test
    void answersClaudeCodesHooksInTheJsonClaudeCodeActsOn() throws Exception {
        String worker = workerTokenFor("api-hooks");

        HttpResponse<String> denied = hook(worker, "pre-tool-use", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"pkill -f java\"},\"cwd\":\"/tmp\"}");
        HttpResponse<String> allowed = hook(worker, "pre-tool-use", "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"},\"cwd\":\"/tmp\"}");
        HttpResponse<String> stopped = hook(worker, "stop", "{\"hook_event_name\":\"Stop\"}");

        assertThat(denied.statusCode()).isEqualTo(200);
        assertThat(denied.body()).startsWith("{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\",\"permissionDecision\":\"deny\","
                + "\"permissionDecisionReason\":\"EDI's guard refuses this: pkill signals every process");
        assertThat(allowed.statusCode()).isEqualTo(204);
        assertThat(stopped.statusCode()).isEqualTo(204);
        assertRefusal(hook(worker, "teleport", "{}"), 400, "EDI answers the hooks session-start, user-prompt-submit, pre-tool-use, stop, stop-failure, notification and session-end, not teleport.");
        assertThat(send(authorised("/api/hooks/pre-tool-use", JSON).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"tmux kill-server\"}}"))).body())
                .contains("\"permissionDecision\":\"deny\"", "name the socket with -L or -S");
    }

    private String workerTokenFor(String task) throws Exception {
        post("/api/tasks", "{\"id\":\"" + task + "\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Fit the lamp\",\"key\":\"HRN-3\",\"base\":\"develop\"}", TEXT);
        TaskId id = new TaskId(task);
        dispatches.save(Dispatch.requested(id, CategoryId.IMPLEMENTATION, BranchName.forWork(BranchKind.FEATURE, WorkKey.parse("HRN-3")), "", null, null,
                Instant.parse("2026-10-15T09:00:00Z")));
        WorkerTokens.IssuedToken token = workerTokens.issueInto(HOME.resolve("state/workers/" + task));
        workers.insert(Worker.launched(WorkerId.first(id), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi-test", 1, Instant.parse("2026-10-15T09:00:00Z"), "@1", 2),
                new WorkerLaunch(LaunchKind.DISPATCH, Instant.parse("2026-10-15T09:00:00Z"), null)), token.digest());
        return Files.readString(token.file());
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
                .header("Authorization", "Bearer " + Files.readString(HOME.resolve("state/core.token")))
                .header("Accept", accepted);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request.build(), BodyHandlers.ofString());
        }
    }

    private static URI uri(String path) throws IOException {
        return URI.create("http://127.0.0.1:" + Files.readString(HOME.resolve("state/core.port")).strip() + path);
    }

    private static Path registeredHome() {
        try {
            Path home = Files.createTempDirectory("edi-api-");
            Files.createDirectories(home.resolve("config"));
            Files.writeString(home.resolve("config/projects.yaml"), Intakes.REGISTRY);
            return home;
        } catch (IOException unwritable) {
            throw new IllegalStateException(unwritable);
        }
    }
}
