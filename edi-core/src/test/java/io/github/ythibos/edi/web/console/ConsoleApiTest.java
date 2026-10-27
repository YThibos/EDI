package io.github.ythibos.edi.web.console;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.StandInScripts;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class ConsoleApiTest {

    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";
    private static final String CONSOLE_HEADER = "X-EDI-Console";
    private static final Duration STREAM_PATIENCE = Duration.ofSeconds(10);

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
        Files.createDirectories(home.resolve("windows"));
        Files.writeString(home.resolve("config/projects.yaml"), Intakes.REGISTRY);
        StandInScripts.installed(home.resolve("bin/browser"), "#!/bin/sh\nprintf '%s\\n' \"$*\" > '" + home.resolve("opened-in-the-browser") + "'\n");
        StandInScripts.installed(home.resolve("bin/lavish-axi"), "#!/bin/sh\nprintf 'session:\\n  file: board.html\\n  status: browser_disconnected\\n'\n");
    }

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
        properties.add("edi.workers.socket", () -> "edi-test-" + UUID.randomUUID());
        properties.add("edi.console.browser", () -> home.resolve("bin/browser").toString());
        properties.add("edi.console.windows-directory", () -> home.resolve("windows").toString());
        properties.add("edi.boards.lavish", () -> home.resolve("bin/lavish-axi").toString());
        properties.add("edi.boards.round", () -> "PT1H");
    }

    @Test
    void opensTheConsoleInTheBrowserThroughALinkThatSignsInOnce() throws Exception {
        HttpResponse<String> opened = post(withTheHomeToken("/api/console/open", TEXT), "{}");
        String link = Files.readString(home.resolve("opened-in-the-browser")).strip().split(" ")[2];

        HttpResponse<String> signedIn = signIn(tokenOf(link));
        HttpResponse<String> usedAgain = signIn(tokenOf(link));

        assertThat(opened.statusCode()).isEqualTo(200);
        assertThat(opened.body()).isEqualTo("EDI console: " + console() + " is open in your browser.\n");
        assertThat(link).startsWith(console() + "login#");
        assertThat(signedIn.statusCode()).isEqualTo(204);
        assertThat(signedIn.headers().firstValue("Set-Cookie")).hasValueSatisfying(cookie -> assertThat(cookie)
                .startsWith("EDI-Console=").contains("Path=/", "Max-Age=43200", "HttpOnly", "SameSite=Strict"));
        assertThat(usedAgain.statusCode()).isEqualTo(401);
        assertThat(usedAgain.body()).isEqualTo("This sign-in link was used already or has expired; run edi console for a new one.\n");
    }

    @Test
    void servesTheSitrepToTheCaptainsSessionAndRefusesEveryoneWithoutOne() throws Exception {
        String cookie = signedInCookie();

        HttpResponse<String> sitrep = send(withTheCookie("/api/sitrep", cookie, JSON).GET());
        HttpResponse<String> forged = send(withTheCookie("/api/sitrep", "EDI-Console=forged", JSON).GET());
        HttpResponse<String> wrongTokenWithACookie = send(withTheCookie("/api/sitrep", cookie, JSON).header("Authorization", "Bearer not-a-token").GET());

        assertThat(sitrep.statusCode()).isEqualTo(200);
        assertThat(wrongTokenWithACookie.statusCode()).isEqualTo(401);
        assertThat(sitrep.body()).contains("\"schema\":\"edi-sitrep.v1\"", "\"address\":\"Commander\"", "\"machine\":\"this machine\"");
        assertThat(forged.statusCode()).isEqualTo(401);
    }

    @Test
    void recordsAConsoleAnswerVerbatimOnlyWithTheConsolesHeader() throws Exception {
        String cookie = signedInCookie();
        String number = raisedDecision("console-lamp");
        String answer = "{\"option\":\"A\",\"text\":\"A, and keep \\\"one\\\" oil lamp\\non the bridge\"}";

        HttpResponse<String> withoutHeader = send(withTheCookie("/api/decisions/" + number + "/answer", cookie).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString(answer)));
        HttpResponse<String> answered = send(withTheCookie("/api/decisions/" + number + "/answer", cookie).header(CONSOLE_HEADER, "1")
                .header("Content-Type", JSON).POST(BodyPublishers.ofString(answer)));
        String recorded = send(withTheHomeToken("/api/decisions", JSON).GET()).body();

        assertThat(withoutHeader.statusCode()).isEqualTo(403);
        assertThat(withoutHeader.body()).isEqualTo("A console request that changes the fleet carries the console's own header, X-EDI-Console.\n");
        assertThat(answered.statusCode()).isEqualTo(200);
        assertThat(answered.body()).isEqualTo("Recorded the answer to " + number + " (option A); console-lamp is done.\n");
        assertThat(recorded).contains("\"verbatim\":\"A, and keep \\\"one\\\" oil lamp\\non the bridge\"", "\"channel\":\"console\"");
        assertThat(send(withTheCookie("/api/sitrep", cookie, JSON).GET()).body()).contains("\"answered\":[{\"number\":\"" + number + "\"");
    }

    @Test
    void asksForAReCheckFromTheConsoleAndKeepsTheConsoleOutOfThePrimarysCommands() throws Exception {
        String cookie = signedInCookie();
        String number = raisedDecision("console-horn");

        HttpResponse<String> recheck = send(withTheCookie("/api/decisions/" + number + "/recheck", cookie).header(CONSOLE_HEADER, "1")
                .POST(BodyPublishers.noBody()));
        HttpResponse<String> addTask = send(withTheCookie("/api/tasks", cookie).header(CONSOLE_HEADER, "1").header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Sneak in\"}")));
        HttpResponse<String> openAnother = send(withTheCookie("/api/console/open", cookie).header(CONSOLE_HEADER, "1").POST(BodyPublishers.noBody()));
        HttpResponse<String> primaryRecheck = post(withTheHomeToken("/api/decisions/" + number + "/recheck", TEXT), "{}");

        assertThat(recheck.statusCode()).isEqualTo(200);
        assertThat(recheck.body()).isEqualTo("You asked for a re-check of " + number + " against reality; it stays open until it is answered.\n");
        assertThat(addTask.statusCode()).isEqualTo(403);
        assertThat(addTask.body()).isEqualTo("edi task add is the primary's to run; the console answers decisions and asks for re-checks.\n");
        assertThat(openAnother.statusCode()).isEqualTo(403);
        assertThat(primaryRecheck.statusCode()).isEqualTo(403);
        assertThat(primaryRecheck.body()).isEqualTo("Asking for a re-check is the captain's, given in the EDI console.\n");
    }

    @Test
    void refusesAWorkerTheConsoleAndServesTheSignInPageAsTheConsoleItself() throws Exception {
        String worker = workerTokenFor("console-worker");

        HttpResponse<String> refused = post(withToken(worker, "/api/console/open", TEXT), "{}");
        HttpResponse<String> page = send(HttpRequest.newBuilder(uri("/login")).GET());

        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(refused.body()).isEqualTo("edi console runs under the EDI home's own token, in the captain's terminal or the primary session.\n");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("<app-root></app-root>");
    }

    @Test
    void makesTheBrowserFetchTheConsoleAgainSoAnUpdatedConsoleShowsAtOnce() throws Exception {
        HttpResponse<String> console = send(HttpRequest.newBuilder(uri("/")).GET());
        HttpResponse<String> signIn = send(HttpRequest.newBuilder(uri("/login")).GET());

        Stream.of(console, signIn).forEach(page -> {
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.headers().firstValue("Cache-Control")).contains("no-cache");
            assertThat(page.headers().firstValue("Last-Modified")).isEmpty();
        });
    }

    @Test
    void armsAResearchBoardForTheWorkerOfItsTask() throws Exception {
        String worker = workerTokenFor("console-scout");
        Path board = Files.writeString(Files.createDirectories(home.resolve("data/tasks/console-scout")).resolve("board.html"), "<!doctype html>\n");

        String round = "{\"file\":\"" + board + "\",\"task\":\"console-scout\",\"link\":\"" + BoardRounds.LINK + "\",\"questions\":2}";

        HttpResponse<String> uncounted = post(withToken(worker, "/api/boards", TEXT), "{\"file\":\"" + board + "\",\"task\":\"console-scout\"}");
        HttpResponse<String> armed = post(withToken(worker, "/api/boards", TEXT), round);
        HttpResponse<String> byThePrimary = post(withTheHomeToken("/api/boards", TEXT), round);

        assertThat(uncounted.statusCode()).isEqualTo(400);
        assertThat(uncounted.body()).isEqualTo("Arming a board counts the questions it puts before the captain.\n");
        assertThat(armed.statusCode()).isEqualTo(201);
        assertThat(armed.body()).isEqualTo("Armed the research board " + board.toRealPath() + " for console-scout; it answers no decision, "
                + "and each round reaches your inbox; the sitrep puts 2 questions before the captain until a round answers them.\n");
        assertThat(byThePrimary.statusCode()).isEqualTo(403);
    }

    @Test
    void raisesTheCardAWorkerArmsItsBoardWithAndBindsItToTheBoard() throws Exception {
        String worker = workerTokenFor("console-closer");
        Path board = Files.writeString(Files.createDirectories(home.resolve("data/tasks/console-closer")).resolve("board.html"), "<!doctype html>\n");
        String card = "{\"question\":\"close-out\",\"title\":\"Close the survey out?\",\"context\":\"The survey is done.\",\"options\":["
                + "{\"key\":\"A\",\"text\":\"Ship the plan\",\"authorises\":\"promote\"},{\"key\":\"B\",\"text\":\"Close it\"}],"
                + "\"recommend\":{\"key\":\"A\",\"why\":\"the plan is ready\"}}";
        String round = "{\"file\":\"" + board + "\",\"task\":\"console-closer\",\"link\":\"" + BoardRounds.LINK + "\",\"questions\":0,\"card\":";

        HttpResponse<String> unnamed = post(withToken(worker, "/api/boards", TEXT), round + card.replace("\"question\":\"close-out\",", "") + "}");
        HttpResponse<String> armed = post(withToken(worker, "/api/boards", TEXT), round + card + "}");

        assertThat(unnamed.statusCode()).isEqualTo(400);
        assertThat(unnamed.body()).startsWith("A decision card on a board names the board question that answers it");
        assertThat(armed.statusCode()).isEqualTo(201);
        assertThat(armed.body()).matches("Armed the research board " + Pattern.quote(board.toRealPath().toString()) + " for console-closer; "
                + "its answers to D\\d+ \\(question close-out\\) are recorded as the captain's, and each round reaches your inbox; "
                + "the sitrep puts its bound decisions before the captain under the board until a round answers them\\.\n");
    }

    @Test
    void letsOnlyThePrimaryPostANewRoundOnAListeningBoard() throws Exception {
        String worker = workerTokenFor("console-surveyor");
        Path board = Files.writeString(Files.createDirectories(home.resolve("data/tasks/console-surveyor")).resolve("board.html"), "<!doctype html>\n");
        post(withToken(worker, "/api/boards", TEXT), "{\"file\":\"" + board + "\",\"task\":\"console-surveyor\",\"link\":\"" + BoardRounds.LINK
                + "\",\"questions\":1}");
        String round = "{\"file\":\"" + board + "\",\"link\":\"" + BoardRounds.LINK + "\",\"questions\":3}";

        HttpResponse<String> byTheScout = post(withToken(worker, "/api/boards/rounds", TEXT), round);
        HttpResponse<String> byThePrimary = post(withTheHomeToken("/api/boards/rounds", TEXT), round);

        assertThat(byTheScout.statusCode()).isEqualTo(403);
        assertThat(byThePrimary.statusCode()).isEqualTo(201);
        assertThat(byThePrimary.body()).isEqualTo("Posted a new round on the research board " + board.toRealPath() + " for console-surveyor;"
                + " the sitrep puts 3 questions before the captain until a round answers them.\n");
    }

    @Test
    void streamsASignalToTheConsoleWheneverTheFleetChanges() throws Exception {
        String cookie = signedInCookie();
        HttpClient http = HttpClient.newHttpClient();
        try {
            HttpResponse<Stream<String>> stream = http.sendAsync(withTheCookie("/api/stream", cookie, "text/event-stream").GET().build(),
                    BodyHandlers.ofLines()).get(STREAM_PATIENCE.toSeconds(), TimeUnit.SECONDS);
            CompletableFuture<Optional<String>> firstSignal = CompletableFuture.supplyAsync(
                    () -> stream.body().filter(line -> line.startsWith("event:")).findFirst());

            post(withTheHomeToken("/api/tasks", TEXT), "{\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Raise the signal\"}");

            assertThat(stream.statusCode()).isEqualTo(200);
            assertThat(firstSignal.get(STREAM_PATIENCE.toSeconds(), TimeUnit.SECONDS)).contains("event:fleet");
        } finally {
            http.shutdownNow();
        }
    }

    @Test
    void choosesPresentOrADemoFromTheConsoleAndNothingElse() throws Exception {
        String cookie = signedInCookie();

        HttpResponse<String> demo = send(withTheCookie("/api/presence", cookie).header(CONSOLE_HEADER, "1").header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"mode\":\"demo\"}")));
        String sitrep = send(withTheCookie("/api/sitrep", cookie, JSON).GET()).body();
        HttpResponse<String> quiet = send(withTheCookie("/api/presence", cookie).header(CONSOLE_HEADER, "1").header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"mode\":\"quiet\"}")));
        HttpResponse<String> demoOff = send(withTheCookie("/api/presence", cookie).header(CONSOLE_HEADER, "1").header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"mode\":\"demo-off\"}")));

        assertThat(demo.statusCode()).isEqualTo(200);
        assertThat(demo.body()).startsWith("Presence: present for a demo until ");
        assertThat(sitrep).contains("\"presence\":{\"mode\":\"present\",\"demo\":true,\"words\":\"present for a demo until ");
        assertThat(quiet.statusCode()).isEqualTo(403);
        assertThat(quiet.body()).isEqualTo("The console sets presence to present or to a demo; quiet and away are set in the chat.\n");
        assertThat(demoOff.body()).isEqualTo("Presence: present.\n");
    }

    @Test
    void showsAndKeepsThePersonalityFromTheConsoleThroughTheSameCoreAsEdiPersonality() throws Exception {
        String cookie = signedInCookie();
        String change = "{\"change\":[\"humour\",\"2\",\"formality\",\"10\"]}";

        HttpResponse<String> shown = send(withTheCookie("/api/personality", cookie, JSON).GET());
        HttpResponse<String> withoutHeader = send(withTheCookie("/api/personality", cookie, JSON).header("Content-Type", JSON)
                .POST(BodyPublishers.ofString(change)));
        HttpResponse<String> kept = send(withTheCookie("/api/personality", cookie, JSON).header(CONSOLE_HEADER, "1").header("Content-Type", JSON)
                .POST(BodyPublishers.ofString(change)));
        HttpResponse<String> told = send(withTheHomeToken("/api/personality", TEXT).GET());
        HttpResponse<String> toAnyone = send(withTheHomeToken("/api/personality", "*/*").GET());

        assertThat(shown.statusCode()).isEqualTo(200);
        assertThat(shown.body()).startsWith("{\"address\":\"Commander\",\"chosen\":false,\"demoHolds\":false,\"levels\":{\"humour\":3,\"formality\":5,"
                + "\"verbosity\":5,\"warmth\":4,\"directness\":6,\"initiative\":5,\"depth\":5,\"enthusiasm\":3,\"flavour\":5},")
                .contains("\"presets\":[{\"name\":\"Balanced\",", "{\"name\":\"Plain\",\"levels\":{\"humour\":0,", "{\"name\":\"Demo\",");
        assertThat(withoutHeader.statusCode()).isEqualTo(403);
        assertThat(kept.statusCode()).isEqualTo(200);
        assertThat(kept.body()).contains("\"chosen\":true", "\"levels\":{\"humour\":2,\"formality\":10,\"verbosity\":5,");
        assertThat(told.body()).startsWith("Personality: humour 2, formality 10, verbosity 5,");
        assertThat(toAnyone.statusCode()).isEqualTo(200);
    }

    private String raisedDecision(String task) throws Exception {
        post(withTheHomeToken("/api/tasks", TEXT), "{\"id\":\"" + task + "\",\"kind\":\"captain\",\"title\":\"Choose the lamp\"}");
        return post(withTheHomeToken("/api/tasks/" + task + "/decision", TEXT), """
                {"title":"Which lamp?","context":"","options":[{"key":"A","text":"LED"},{"key":"B","text":"Oil"}],"recommend":{"key":"A","why":"it lasts"}}""")
                .body().strip();
    }

    private String signedInCookie() throws Exception {
        post(withTheHomeToken("/api/console/open", TEXT), "{}");
        String link = Files.readString(home.resolve("opened-in-the-browser")).strip().split(" ")[2];
        String setCookie = signIn(tokenOf(link)).headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static HttpResponse<String> signIn(String token) throws Exception {
        return send(HttpRequest.newBuilder(uri("/api/console/session")).header(CONSOLE_HEADER, "1").header("Content-Type", JSON)
                .POST(BodyPublishers.ofString("{\"token\":\"" + token + "\"}")));
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf('#') + 1);
    }

    private String workerTokenFor(String task) throws Exception {
        post(withTheHomeToken("/api/tasks", TEXT), "{\"id\":\"" + task + "\",\"kind\":\"scout\",\"projects\":[\"harness\"],\"title\":\"Map the caves\","
                + "\"key\":\"HRN-3\"}");
        TaskId id = new TaskId(task);
        dispatches.save(Dispatch.requested(id, CategoryId.RESEARCH, BranchName.forWork(BranchKind.RESEARCH, WorkKey.parse("HRN-3")), "", null, null,
                Instant.parse("2026-10-15T09:00:00Z")));
        WorkerTokens.IssuedToken token = workerTokens.issueInto(home.resolve("state/workers/" + task));
        workers.insert(Worker.launched(WorkerId.first(id), UUID.randomUUID(), CategoryId.RESEARCH, new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi-test", 1, Instant.parse("2026-10-15T09:00:00Z"), "@1", 2),
                new WorkerLaunch(LaunchKind.DISPATCH, Instant.parse("2026-10-15T09:00:00Z"), null)), token.digest());
        return Files.readString(token.file());
    }

    private static HttpRequest.Builder withTheHomeToken(String path, String accepted) throws IOException {
        return withToken(Files.readString(home.resolve("state/core.token")), path, accepted);
    }

    private static HttpRequest.Builder withToken(String token, String path, String accepted) throws IOException {
        return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token).header("Accept", accepted);
    }

    private static HttpRequest.Builder withTheCookie(String path, String cookie) throws IOException {
        return withTheCookie(path, cookie, TEXT);
    }

    private static HttpRequest.Builder withTheCookie(String path, String cookie, String accepted) throws IOException {
        return HttpRequest.newBuilder(uri(path)).header("Cookie", cookie).header("Accept", accepted);
    }

    private static HttpResponse<String> post(HttpRequest.Builder request, String json) throws Exception {
        return send(request.header("Content-Type", JSON).POST(BodyPublishers.ofString(json)));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request.build(), BodyHandlers.ofString());
        }
    }

    private static String console() throws IOException {
        return "http://127.0.0.1:" + Files.readString(home.resolve("state/core.port")).strip() + "/";
    }

    private static URI uri(String path) throws IOException {
        return URI.create("http://127.0.0.1:" + Files.readString(home.resolve("state/core.port")).strip() + path);
    }
}
