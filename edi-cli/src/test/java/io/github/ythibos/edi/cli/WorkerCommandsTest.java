package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

@Tag("adapter")
class WorkerCommandsTest {

    @TempDir
    private Path home;

    private StubCore core;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home);
        environment.put("EDI_HOME", home.toString());
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void dispatchesATaskWithItsSpecAndTheCaptainsOverride() {
        core.answering(200, "Dispatched fit-lamp to fit-lamp#1.\n");

        int exitCode = edi("Fit the brass lamp.", "dispatch", "fit-lamp", "--category", "implementation", "--spec", "-",
                "--override-model", "claude-opus-5-5", "--override-effort", "high", "--reason", "the captain asked for Opus");

        assertThat(exitCode).isZero();
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/dispatch");
        assertThat(core.received().body()).isEqualTo("{\"category\":\"implementation\",\"spec\":\"Fit the brass lamp.\","
                + "\"overrideModel\":\"claude-opus-5-5\",\"overrideEffort\":\"high\",\"reason\":\"the captain asked for Opus\"}");
    }

    @Test
    void previewsABriefWithACategoryAndReadsTheWrittenOneWithout() {
        core.answering(200, "# Brief: fit-lamp\n");

        edi("", "brief", "fit-lamp", "--category", "mechanical", "--files", "src/Lamp.java,src/Horn.java");
        StubCore.Request preview = core.received();
        edi("", "brief", "fit-lamp");

        assertThat(preview.method()).isEqualTo("POST");
        assertThat(preview.body()).isEqualTo("{\"category\":\"mechanical\",\"files\":[\"src/Lamp.java\",\"src/Horn.java\"]}");
        assertThat(core.received().method()).isEqualTo("GET");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/brief");
    }

    @Test
    void reportsAStatusUnderTheWorkersOwnToken() throws IOException {
        Path tokenFile = Files.writeString(home.resolve("worker.token"), "worker-token\n");
        environment.put("EDI_TOKEN_FILE", tokenFile.toString());
        core.answering(200, "Recorded paused for fit-lamp#1.\n");

        int exitCode = edi("", "status", "paused", "--key", "build", "--until", "2026-10-16T14:30:00Z", "--", "waiting", "for", "the", "--slow", "build");

        assertThat(exitCode).isZero();
        assertThat(core.received().authorization()).isEqualTo("Bearer worker-token");
        assertThat(core.received().body()).isEqualTo("{\"state\":\"paused\",\"key\":\"build\",\"until\":\"2026-10-16T14:30:00Z\","
                + "\"text\":\"waiting for the --slow build\"}");
    }

    @Test
    void readsALongStatusFromStandardInput() {
        core.answering(200, "Recorded done for fit-lamp#1.\n");

        edi("The lamp is fitted.\nThe horn too.", "status", "done", "-");

        assertThat(core.received().body()).isEqualTo("{\"state\":\"done\",\"text\":\"The lamp is fitted.\\nThe horn too.\"}");
    }

    @Test
    void readsAndAcknowledgesTheInboxAndSteersFromStandardInput() {
        core.answering(200, "ok\n");

        edi("", "inbox");
        assertThat(core.received().target()).isEqualTo("/api/inbox");
        edi("", "inbox", "ack", "2");
        assertThat(core.received().target()).isEqualTo("/api/inbox/2/ack");
        edi("Use the brass lamp.", "steer", "fit-lamp", "--resolves", "lamp-choice");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/steer");
        assertThat(core.received().body()).isEqualTo("{\"text\":\"Use the brass lamp.\",\"resolves\":\"lamp-choice\"}");
        edi("", "worker", "peek", "fit-lamp");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/peek");
        edi("", "worker", "list");
        assertThat(core.received().target()).isEqualTo("/api/workers");
    }

    @Test
    void passesAHookPayloadThroughAndPrintsOnlyWhatTheCoreAnswers() {
        core.answering(204, "");

        int nothing = edi("{\"hook_event_name\":\"Stop\"}", "hook", "stop");
        String printedForNothing = out.toString();
        core.answering(200, "{\"decision\":\"block\",\"reason\":\"A message is waiting.\"}");
        int blocked = edi("{\"hook_event_name\":\"Stop\"}", "hook", "stop");

        assertThat(nothing).isZero();
        assertThat(printedForNothing).isEmpty();
        assertThat(blocked).isZero();
        assertThat(out).hasToString("{\"decision\":\"block\",\"reason\":\"A message is waiting.\"}");
        assertThat(core.received().accepted()).isEqualTo("application/json");
        assertThat(core.received().body()).isEqualTo("{\"hook_event_name\":\"Stop\"}");
    }

    @Test
    void blocksAToolCallWhenTheGuardCannotBeAskedAndLetsOtherHooksPass() {
        core.close();

        int guarded = edi("{\"tool_name\":\"Bash\"}", "hook", "pre-tool-use");
        String guardError = err.toString();
        int stopped = edi("{}", "hook", "stop");

        assertThat(guarded).isEqualTo(2);
        assertThat(guardError).startsWith("EDI's guard could not check this tool call, so it is refused: EDI core is not answering");
        assertThat(stopped).isOne();
    }

    @Test
    void waitsUntilTheCoreHasAWakeAndPrintsIt() {
        environment.put("CLAUDE_CODE_SESSION_ID", "6d1f0c2e-0000-4000-8000-000000000001");
        core.thenAnswering(204, "").thenAnswering(204, "").thenAnswering(200, "EDI wake: 1 waiting.\n1. fit-lamp#1 reports done: fitted\n");

        int exitCode = edi("", "wait");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI wake: 1 waiting.\n1. fit-lamp#1 reports done: fitted\n");
        assertThat(core.requests()).hasSize(3).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.target()).isEqualTo("/api/wait");
            assertThat(request.session()).isEqualTo("6d1f0c2e-0000-4000-8000-000000000001");
        });
    }

    @Test
    void waitsOnlyInsideAClaudeCodeSessionAndStopsWhenTheCoreRefuses() {
        int outside = edi("", "wait");
        String outsideError = err.toString();
        environment.put("CLAUDE_CODE_SESSION_ID", "6d1f0c2e-0000-4000-8000-000000000001");
        core.answering(409, "edi wait serves the primary session, the one the captain typed in last; this session is not it.\n");
        int refused = edi("", "wait");

        assertThat(outside).isOne();
        assertThat(outsideError).startsWith("edi wait runs inside the primary's Claude Code session");
        assertThat(refused).isOne();
        assertThat(err.toString()).endsWith("this session is not it.\n");
    }

    @Test
    void namesTheClaudeCodeSessionAndProcessOnEveryCall() {
        environment.put("CLAUDE_CODE_SESSION_ID", "6d1f0c2e-0000-4000-8000-000000000002");
        environment.put("CLAUDE_PID", "4242");
        core.answering(200, "Backlog (0)\n");

        edi("", "backlog");

        assertThat(core.received().session()).isEqualTo("6d1f0c2e-0000-4000-8000-000000000002");
        assertThat(core.received().claudePid()).isEqualTo("4242");
    }

    @Test
    void buildsTheOutageHoldListAndActsOnTheNumberTheCaptainNamed() {
        core.answering(200, "ok\n");

        edi("", "recover");
        StubCore.Request listed = core.received();
        edi("", "recover", "2", "resume");
        StubCore.Request resumed = core.received();
        edi("", "recover", "4", "fresh", "--effort", "medium");
        StubCore.Request fresh = core.received();
        int incomplete = edi("", "recover", "3");

        assertThat(listed.target()).isEqualTo("/api/recovery");
        assertThat(listed.method()).isEqualTo("POST");
        assertThat(resumed.target()).isEqualTo("/api/recovery/2/resume");
        assertThat(resumed.body()).isEqualTo("{}");
        assertThat(fresh.target()).isEqualTo("/api/recovery/4/fresh");
        assertThat(fresh.body()).isEqualTo("{\"effort\":\"medium\"}");
        assertThat(incomplete).isEqualTo(2);
        assertThat(err.toString()).contains("Name resume or fresh after number 3, as the captain did.");
    }

    @Test
    void relaunchesAndStopsATasksWorker() {
        core.answering(200, "ok\n");

        edi("", "worker", "relaunch", "fit-lamp");
        StubCore.Request relaunched = core.received();
        edi("", "worker", "stop", "fit-lamp");

        assertThat(relaunched.target()).isEqualTo("/api/tasks/fit-lamp/relaunch");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/stop");
        assertThat(core.received().method()).isEqualTo("POST");
    }

    private int edi(String input, String... arguments) {
        return new CommandLine(new Edi(Map.copyOf(environment), new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }
}
