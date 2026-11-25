package io.github.ythibos.edi.web.board;

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
import java.nio.file.StandardCopyOption;
import java.time.Instant;
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

import io.github.ythibos.edi.adapter.runtime.PrivateTmuxServer;
import io.github.ythibos.edi.adapter.store.DispatchStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.worker.WorkerTokens;
import io.github.ythibos.edi.web.access.PrimaryLaunchKey;
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
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.StandInScripts;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class BoardReceiptEndToEndTest {

    private static final String SCOUT = "ended-scout";
    private static final String WORKING = "Working...\n";
    private static final Instant BEGAN = Instant.parse("2026-11-25T09:00:00Z");
    private static final String LAVISH = """
            #!/bin/sh
            here="$(dirname "$0")/.."
            case "$1" in
            reply)
                printf 'Replied: %s\\n' "$4" > "$here/board-state"
                ;;
            poll)
                if [ -f "$here/answers-taken" ]; then
                    printf 'session:\\n  file: %s\\n  status: browser_disconnected\\n' "$2"
                    exit 0
                fi
                while [ ! -f "$here/answers.toon" ]; do sleep 0.1; done
                touch "$here/answers-taken"
                cat "$here/answers.toon"
                ;;
            esac
            """;

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
        StandInScripts.installed(home.resolve("bin/lavish-axi"), LAVISH);
        Files.writeString(home.resolve("board-state"), WORKING);
    }

    @DynamicPropertySource
    static void useTheTemporaryHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
        properties.add("edi.workers.socket", () -> "edi-test-" + UUID.randomUUID());
        properties.add("edi.boards.lavish", () -> home.resolve("bin/lavish-axi").toString());
        properties.add("edi.boards.round", () -> "PT1H");
    }

    @Test
    void takesTheBoardOutOfItsWorkingStateWhenTheCaptainAnswersAfterItsScoutEnded() throws Exception {
        String worker = workerTokenFor(SCOUT);
        Path board = Files.writeString(Files.createDirectories(home.resolve("data/tasks/" + SCOUT)).resolve("board.html"), "<!doctype html>\n");
        post(withToken(worker, "/api/boards"), "{\"file\":\"" + board + "\",\"task\":\"" + SCOUT + "\",\"link\":\"" + BoardRounds.LINK + "\",\"questions\":2}");
        endTheScout();
        String before = Files.readString(home.resolve("board-state"));

        Files.move(Files.writeString(home.resolve("answers.toon.part"), answersOn(board)), home.resolve("answers.toon"), StandardCopyOption.ATOMIC_MOVE);

        String after = PrivateTmuxServer.awaitContent(home.resolve("board-state"), "Replied");
        assertThat(before).isEqualTo(WORKING);
        assertThat(after).isEqualTo("Replied: Received 2 answers in round 1. None of them names a decision. If I cannot record one of them, I tell you in the session.\n");
    }

    private void endTheScout() {
        Worker scout = workers.get(WorkerId.first(new TaskId(SCOUT))).orElseThrow();
        workers.update(scout.retired(Instant.parse("2026-11-25T10:00:00Z")));
    }

    private static String answersOn(Path board) {
        return """
                session:
                  file: %s
                  status: feedback
                prompts[2]{uid,prompt,selector,tag,text}:
                  "1","Brighter, please.","section#s1 > p",p,"Fittings: brass, steel or bronze."
                  "",Thank you.,"",message,Freeform message
                """.formatted(board);
    }

    private String workerTokenFor(String task) throws Exception {
        HttpResponse<String> created = post(withTheHomeToken("/api/tasks"), "{\"id\":\"" + task + "\",\"kind\":\"scout\",\"projects\":[\"harness\"],\"title\":\"Map the caves\",\"key\":\"HRN-3\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        TaskId id = new TaskId(task);
        dispatches.save(Dispatch.requested(id, CategoryId.RESEARCH, BranchName.forWork(BranchKind.RESEARCH, WorkKey.parse("HRN-3")), "", null, null, BEGAN));
        WorkerTokens.IssuedToken token = workerTokens.issueInto(home.resolve("state/workers/" + task));
        workers.insert(Worker.launched(WorkerId.first(id), UUID.randomUUID(), CategoryId.RESEARCH, new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH),
                new WorkerWindow("edi-test", 1, BEGAN, "@1", 2), new WorkerLaunch(LaunchKind.DISPATCH, BEGAN, null, LoginName.DEFAULT)), token.digest());
        return Files.readString(token.file());
    }

    private static HttpRequest.Builder withTheHomeToken(String path) throws IOException {
        return withToken(Files.readString(home.resolve("state/core.token")), path)
                .header(PrimaryLaunchKey.HEADER, Files.readString(home.resolve("state/primary-launch.key")).strip());
    }

    private static HttpRequest.Builder withToken(String token, String path) throws IOException {
        URI address = URI.create("http://127.0.0.1:" + Files.readString(home.resolve("state/core.port")).strip() + path);
        return HttpRequest.newBuilder(address).header("Authorization", "Bearer " + token).header("Accept", "text/plain");
    }

    private static HttpResponse<String> post(HttpRequest.Builder request, String json) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request.header("Content-Type", "application/json").POST(BodyPublishers.ofString(json)).build(), BodyHandlers.ofString());
        }
    }
}
