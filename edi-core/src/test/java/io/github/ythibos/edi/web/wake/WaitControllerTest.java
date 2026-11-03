package io.github.ythibos.edi.web.wake;

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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.wake.WakeSignal;
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
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.Patience;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class WaitControllerTest {

    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";

    @TempDir
    private static Path home;

    @Autowired
    private WitnessedWakeSignal signal;

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
        properties.add("edi.supervision.round", () -> "PT1H");
    }

    @Test
    void holdsAWaitOpenUntilAWorkerWakesThePrimary() throws Exception {
        String worker = workerTokenFor("held-wake");
        UUID primary = UUID.randomUUID();
        primaryHook(primary, "session-start", "{\"source\":\"startup\"}");
        primaryHook(primary, "user-prompt-submit", "{\"prompt\":\"Keep watch on the buoy.\"}");

        UUID stream = UUID.randomUUID();

        try (HttpClient http = HttpClient.newHttpClient()) {
            CompletableFuture<HttpResponse<String>> held = http.sendAsync(waitIn(primary, stream).build(), BodyHandlers.ofString());
            assertThat(signal.aRequestWaits()).isTrue();
            assertThat(held).isNotDone();
            HttpResponse<String> second = send(waitIn(primary, UUID.randomUUID()));

            needsADecision(worker, "{\"state\":\"needs-decision\",\"key\":\"buoy\",\"text\":\"Bell or whistle?\"}");
            HttpResponse<String> woken = held.get(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS);
            primaryHook(primary, "session-end", "{\"reason\":\"prompt_input_exit\"}");

            assertThat(second.statusCode()).isEqualTo(409);
            assertThat(second.body()).contains("already live as edi wait --follow in process " + ProcessHandle.current().pid());
            assertThat(woken.statusCode()).isEqualTo(200);
            assertThat(woken.headers().firstValue("EDI-Wakes")).hasValueSatisfying(numbers -> assertThat(numbers).matches("\\d+"));
            assertThat(woken.body()).startsWith("EDI wake: ").contains("held-wake#1 needs a decision: (buoy) Bell or whistle?")
                    .endsWith("Handle these; the wake stream keeps running and prints the next.\n");
        }
    }

    private String workerTokenFor(String task) throws Exception {
        post("/api/tasks", "{\"id\":\"" + task + "\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Fit the lamp\",\"key\":\"HRN-3\",\"base\":\"develop\"}");
        TaskId id = new TaskId(task);
        dispatches.save(Dispatch.requested(id, CategoryId.IMPLEMENTATION, BranchName.forWork(BranchKind.FEATURE, WorkKey.parse("HRN-3")), "", null, null,
                Instant.parse("2026-10-15T09:00:00Z")));
        WorkerTokens.IssuedToken token = workerTokens.issueInto(home.resolve("state/workers/" + task));
        workers.insert(Worker.launched(WorkerId.first(id), UUID.randomUUID(), CategoryId.IMPLEMENTATION,
                new Route(new ModelId("claude-sonnet-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi-test", 1, Instant.parse("2026-10-15T09:00:00Z"), "@1", 2),
                new WorkerLaunch(LaunchKind.DISPATCH, Instant.parse("2026-10-15T09:00:00Z"), null)), token.digest());
        return Files.readString(token.file());
    }

    private static HttpResponse<String> primaryHook(UUID session, String event, String fields) throws Exception {
        String payload = "{\"session_id\":\"" + session + "\"," + fields.substring(1);
        return send(inSession(session, "/api/hooks/" + event, JSON).header("Content-Type", JSON).POST(BodyPublishers.ofString(payload)));
    }

    private static HttpRequest.Builder waitIn(UUID session, UUID stream) throws IOException {
        String poll = "{\"stream\":\"" + stream + "\",\"pid\":" + ProcessHandle.current().pid() + ",\"follows\":true}";
        return inSession(session, "/api/wait", TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString(poll));
    }

    private static void needsADecision(String worker, String status) throws Exception {
        send(HttpRequest.newBuilder(uri("/api/status")).header("Authorization", "Bearer " + worker).header("Accept", TEXT)
                .header("Content-Type", JSON).POST(BodyPublishers.ofString(status)));
    }

    private static HttpResponse<String> post(String path, String json) throws Exception {
        return send(authorised(path, TEXT).header("Content-Type", JSON).POST(BodyPublishers.ofString(json)));
    }

    private static HttpRequest.Builder inSession(UUID session, String path, String accepted) throws IOException {
        return authorised(path, accepted).header("EDI-Session", session.toString());
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

    @TestConfiguration(proxyBeanMethods = false)
    static class WitnessedWakes {

        @Bean
        @Primary
        WitnessedWakeSignal witnessedWakeSignal() {
            return new WitnessedWakeSignal();
        }
    }

    static class WitnessedWakeSignal extends WakeSignal {

        private final Semaphore waitingRequests = new Semaphore(0);

        @Override
        public void onNextWake(Runnable waiter) {
            super.onNextWake(waiter);
            waitingRequests.release();
        }

        boolean aRequestWaits() throws InterruptedException {
            return waitingRequests.tryAcquire(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS);
        }
    }
}
