package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.StubJiraServer;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class TrackerApiTest {

    private static final String TOKEN = "jira-token-for-the-stub-only";
    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";

    @TempDir
    private static Path home;

    private static StubJiraServer jira;

    @BeforeAll
    static void connectTheHomeToAStubJiraWhoseWorkflowRunsBackToProductionInNineSteps() throws IOException {
        jira = StubJiraServer.acceptingToken(TOKEN)
                .holding("SVC-40", "Harbour lamps", "Done")
                .holding("SVC-41", "Harbour bells", "Done")
                .holding("SVC-42", "Harbour flags", "Done")
                .holding("SVC-10", "Harbour buoys", "Backlog")
                .offeringFrom("Done", "Reopened")
                .offeringFrom("Reopened", "Backlog")
                .offeringFrom("Backlog", "Selected")
                .offeringFrom("Selected", "Doing")
                .offeringFrom("Doing", "Peer Review")
                .offeringFrom("Peer Review", "Merged")
                .offeringFrom("Merged", "On Test")
                .offeringFrom("On Test", "Verified")
                .offeringFrom("Verified", "Ready For Prd")
                .offeringFrom("Ready For Prd", "Done")
                .refusingTransitionsTo("Peer Review");
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("keys.yml"), "TRACKER:\n  domain: tracker.example.org\n  API-key: " + TOKEN + "\n");
        Files.writeString(home.resolve("config/machine.yaml"), """
                tracker:
                  kind: jira
                  baseUrl: %s
                  credentials: { keysFile: %s, section: TRACKER }
                  workflow: [Reopened, Backlog, Selected, Doing, Peer Review, Merged, On Test, Verified, Ready For Prd, Done]
                  mergedStatus: Merged
                  issueType: Story
                  reviewStatuses: [Peer Review]
                  implementedStatuses: [Merged, On Test, Verified, Ready For Prd, Done]
                  doneStatus: Done
                """.formatted(jira.address(), home.resolve("keys.yml")));
        Files.writeString(home.resolve("config/projects.yaml"), """
                projects:
                  - name: service
                    tracker: { kind: jira, projectKey: SVC }
                    base: main
                """);
        FixtureRepository.initialise(Files.createDirectories(home.resolveSibling(home.getFileName() + "-upstream")))
                .write("buoy.txt", "red").commit("SVC-10 Paint the buoy")
                .clonedInto(home.resolve("projects/service"));
    }

    @AfterAll
    static void disconnect() {
        jira.close();
    }

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
        properties.add("edi.checkout", () -> home.resolve("checkout").toString());
        properties.add("edi.workers.socket", () -> "edi-test-tracker");
        properties.add("edi.supervision.round", () -> "PT1H");
    }

    @Test
    void walksATicketToTheNamedStateAndAnswersInText() throws Exception {
        jira.holding("SVC-43", "Harbour oars", "Verified");

        HttpResponse<String> walked = post("/api/tracker/walk", "{\"key\":\"SVC-43\",\"state\":\"Ready For Prd\"}", TEXT);

        assertThat(walked.statusCode()).isEqualTo(200);
        assertThat(walked.body()).isEqualTo("SVC-43 moved Verified -> Ready For Prd in 1 step.");
        assertThat(jira.statusOf("SVC-43")).isEqualTo("Ready For Prd");
    }

    @Test
    void stopsAWalkWhereTheTrackerRefusesAStepAndNamesTheStepsTaken() throws Exception {
        HttpResponse<String> stopped = post("/api/tracker/walk", "{\"key\":\"SVC-40\",\"state\":\"Ready For Prd\"}", TEXT);

        assertThat(stopped.statusCode()).isEqualTo(409);
        assertThat(stopped.body()).isEqualTo("SVC-40 stopped at Doing after the steps Done -> Reopened -> Backlog -> Selected -> Doing on its way to Ready For Prd: "
                + "JIRA refused the transition of SVC-40 to Peer Review (HTTP 400): Transition Peer Review is not valid for this issue.\n");
        assertThat(jira.statusOf("SVC-40")).isEqualTo("Doing");
    }

    @Test
    void answersAWalkInJson() throws Exception {
        HttpResponse<String> walked = post("/api/tracker/walk", "{\"key\":\"SVC-42\",\"state\":\"Backlog\"}", JSON);

        assertThat(walked.statusCode()).isEqualTo(200);
        assertThat(walked.body()).contains("\"from\":\"Done\"", "\"through\":[\"Reopened\",\"Backlog\"]");
    }

    @Test
    void refusesAStateOutsideTheWorkflowAsAMalformedCall() throws Exception {
        HttpResponse<String> refused = post("/api/tracker/walk", "{\"key\":\"SVC-42\",\"state\":\"Shipped\"}", TEXT);

        assertThat(refused.statusCode()).isEqualTo(400);
        assertThat(refused.body()).contains("Shipped is not a status of the workflow");
    }

    @Test
    void listsTheDriftOfAProjectAndWhatItCouldNotCheck() throws Exception {
        HttpResponse<String> drift = get("/api/tracker/drift?project=service", TEXT);

        assertThat(drift.statusCode()).isEqualTo(200);
        assertThat(drift.body()).contains("Ticket drift in service", "SVC-10", "Backlog", "work merged, but the ticket is before its merged state",
                "Not checked: a story or bug in Done before its work reached production, because the project's registry entry names no production branch.");
    }

    @Test
    void refusesADriftReportForAProjectThatIsNotRegistered() throws Exception {
        HttpResponse<String> refused = get("/api/tracker/drift?project=nowhere", TEXT);

        assertThat(refused.statusCode()).isEqualTo(409);
        assertThat(refused.body()).contains("nowhere");
    }

    private static HttpResponse<String> get(String path, String accepted) throws Exception {
        return send(authorised(path, accepted).GET());
    }

    private static HttpResponse<String> post(String path, String json, String accepted) throws Exception {
        return send(authorised(path, accepted).header("Content-Type", JSON).POST(BodyPublishers.ofString(json)));
    }

    private static HttpRequest.Builder authorised(String path, String accepted) throws IOException {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + Files.readString(home.resolve("state/core.port")).strip() + path))
                .header("Authorization", "Bearer " + Files.readString(home.resolve("state/core.token")))
                .header("EDI-Primary-Launch-Key", Files.readString(home.resolve("state/primary-launch.key")).strip())
                .header("Accept", accepted);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request.build(), BodyHandlers.ofString());
        }
    }
}
