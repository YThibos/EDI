package io.github.ythibos.edi.web.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.springframework.context.event.EventListener;
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
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.domain.worker.WorkerLaunch;
import io.github.ythibos.edi.domain.worker.WorkerWindow;
import io.github.ythibos.edi.testing.FakeWorkerRuntime;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.Patience;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class LivePaneTest {

    private static final String JSON = "application/json";
    private static final String STREAM = "text/event-stream";

    @TempDir
    private static Path home;

    @Autowired
    private FakeWorkerRuntime runtime;

    @Autowired
    private WorkerStore workers;

    @Autowired
    private DispatchStore dispatches;

    @Autowired
    private WorkerTokens workerTokens;

    @Autowired
    private StoppedPanes stopped;

    @Autowired
    private LivePanes panes;

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
    void streamsThePaneWithItsColoursAndStopsCapturingOnceTheTabCloses() throws Exception {
        servedByAWorker("live-lamp");
        runtime.showsInThePane(List.of("\u001b[31mThe lamp is lit\u001b[0m", "claude-opus-5-5 · high · 41% context"));

        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> stream = http.send(asThePrimary("/api/tasks/live-lamp/live", STREAM).GET().build(), BodyHandlers.ofInputStream());
            BufferedReader events = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8));
            List<String> pane = eventNamed("pane", events);
            stream.body().close();

            assertThat(stream.statusCode()).isEqualTo(200);
            assertThat(pane).containsExactly("{\"lines\":[\"\\u001B[31mThe lamp is lit\\u001B[0m\",\"claude-opus-5-5 · high · 41% context\"]}");
            assertThat(stopped.next()).isEqualTo(new TaskId("live-lamp"));
            assertThat(panes.watching()).isZero();
        }
    }

    @Test
    void endsTheStreamWhenTheWorkersWindowCloses() throws Exception {
        WorkerWindow window = servedByAWorker("live-horn");

        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> stream = http.send(asThePrimary("/api/tasks/live-horn/live", STREAM).GET().build(), BodyHandlers.ofInputStream());
            BufferedReader events = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8));
            eventNamed("pane", events);
            runtime.close(window);
            List<String> ended = eventNamed("ended", events);

            assertThat(ended).singleElement().asString().contains("is no longer on the");
            assertThat(stopped.next()).isEqualTo(new TaskId("live-horn"));
        }
    }

    @Test
    void refusesAWorkerAndATaskWithoutAWorker() throws Exception {
        String worker = workerTokenOf("live-bell");
        post("/api/tasks", "{\"id\":\"live-buoy\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Paint the buoy\",\"key\":\"HRN-4\"}");

        HttpResponse<String> toTheWorker = send(withToken(worker, "/api/tasks/live-bell/live", STREAM).GET());
        HttpResponse<String> noWorker = send(asThePrimary("/api/tasks/live-buoy/live", STREAM).GET());

        assertThat(toTheWorker.statusCode()).isEqualTo(403);
        assertThat(toTheWorker.body()).isEqualTo("A worker's live pane is the primary's and the captain's to read; a worker reads its brief and edi inbox.\n");
        assertThat(noWorker.statusCode()).isEqualTo(409);
        assertThat(noWorker.body()).isEqualTo("live-buoy has no worker; dispatch it first.\n");
    }

    private WorkerWindow servedByAWorker(String task) throws Exception {
        workerTokenOf(task);
        return workers.latestOf(new TaskId(task)).orElseThrow().window();
    }

    private String workerTokenOf(String task) throws Exception {
        post("/api/tasks", "{\"id\":\"" + task + "\",\"kind\":\"ship\",\"projects\":[\"harness\"],\"title\":\"Fit the lamp\",\"key\":\"HRN-3\"}");
        TaskId id = new TaskId(task);
        dispatches.save(Dispatch.requested(id, CategoryId.IMPLEMENTATION, BranchName.forWork(BranchKind.FEATURE, WorkKey.parse("HRN-3")), "", null, null,
                Instant.parse("2026-10-15T09:00:00Z")));
        WorkerTokens.IssuedToken token = workerTokens.issueInto(home.resolve("state/workers/" + task));
        workers.insert(Worker.launched(WorkerId.first(id), UUID.randomUUID(), CategoryId.IMPLEMENTATION, new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH),
                runtime.openWindow(), new WorkerLaunch(LaunchKind.DISPATCH, Instant.parse("2026-10-15T09:00:00Z"), null, LoginName.DEFAULT)), token.digest());
        return Files.readString(token.file());
    }

    private static List<String> eventNamed(String name, BufferedReader events) throws IOException {
        boolean named = false;
        List<String> data = new ArrayList<>();
        for (String line = events.readLine(); line != null; line = events.readLine()) {
            if (line.equals("event:" + name)) {
                named = true;
            } else if (named && line.startsWith("data:")) {
                data.add(line.substring("data:".length()));
            } else if (named && line.isEmpty()) {
                return data;
            }
        }
        return data;
    }

    private static void post(String path, String json) throws Exception {
        send(asThePrimary(path, "text/plain").header("Content-Type", JSON).POST(BodyPublishers.ofString(json)));
    }

    private static HttpRequest.Builder asThePrimary(String path, String accepted) throws IOException {
        return withToken(Files.readString(home.resolve("state/core.token")), path, accepted);
    }

    private static HttpRequest.Builder withToken(String token, String path, String accepted) throws IOException {
        return HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token).header("Accept", accepted);
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
    static class AFakeRuntimeAndAWitness {

        @Bean
        @Primary
        FakeWorkerRuntime fakeWorkerRuntime() {
            return new FakeWorkerRuntime();
        }

        @Bean
        StoppedPanes stoppedPanes() {
            return new StoppedPanes();
        }
    }

    static class StoppedPanes {

        private final BlockingQueue<TaskId> stopped = new LinkedBlockingQueue<>();

        @EventListener
        public void stopped(LivePaneStopped pane) {
            stopped.add(pane.task());
        }

        TaskId next() throws InterruptedException {
            return stopped.poll(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS);
        }
    }
}
