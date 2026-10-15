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
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", HOME::toString);
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
        assertThat(refused.body()).isEqualTo("This call needs the EDI token from state/core.token in the EDI home.\n");
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
