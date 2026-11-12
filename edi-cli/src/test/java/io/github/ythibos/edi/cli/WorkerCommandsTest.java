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
    void showsATasksWorkerFromTheStore() {
        core.answering(200, "fit-lamp#1, implementation, on claude-opus-5-5 at high\n");

        int exitCode = edi("", "worker", "show", "fit-lamp");

        assertThat(exitCode).isZero();
        assertThat(core.received().method()).isEqualTo("GET");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/worker");
        assertThat(out.toString()).isEqualTo("fit-lamp#1, implementation, on claude-opus-5-5 at high\n");
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
    void dispatchesABugWhoseCauseIsUnknown() {
        core.answering(200, "Research comes first.\n");

        int exitCode = edi("", "dispatch", "fix-horn", "--category", "bugfix", "--cause-unknown");

        assertThat(exitCode).isZero();
        assertThat(core.received().body()).isEqualTo("{\"category\":\"bugfix\",\"causeUnknown\":true}");
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
    void refusesAWorkerWhoseTokenFileIsUnsetTheHomeTokenOfThePrimary() {
        inAWorkersWindowWithoutItsTokenFile();
        core.answering(200, "Dispatched fit-horn to fit-horn#1.\n");

        int exitCode = edi("", "dispatch", "fit-horn", "--category", "implementation");

        assertThat(exitCode).isOne();
        assertThat(core.requests()).isEmpty();
        assertThat(err.toString()).startsWith("This shell is worker fit-lamp#1's, yet EDI_TOKEN_FILE names no token file");
    }

    @Test
    void refusesAToolCallOfAWorkerWhoseTokenFileIsUnsetWhileTheCoreIsDown() throws IOException {
        inAWorkersWindowWithoutItsTokenFile();
        core.stopAnswering();

        int guarded = edi("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"edi status working\"}}", "hook", "pre-tool-use");

        assertThat(guarded).isEqualTo(2);
        assertThat(err.toString()).startsWith("The guard could not check this tool call, so it is refused: This shell is worker fit-lamp#1's");
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
    void blocksAToolCallWhenTheGuardCannotBeAskedAndLetsOtherHooksPass() throws IOException {
        core.stopAnswering();

        int guarded = edi("{\"tool_name\":\"Bash\"}", "hook", "pre-tool-use");
        String guardError = err.toString();
        int stopped = edi("{}", "hook", "stop");

        assertThat(guarded).isEqualTo(2);
        assertThat(guardError).startsWith("The guard could not check this tool call, so it is refused: EDI core is not answering");
        assertThat(stopped).isOne();
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
        edi("", "recover", "5", "drop", "--discard-work");
        StubCore.Request dropped = core.received();
        int incomplete = edi("", "recover", "3");

        assertThat(listed.target()).isEqualTo("/api/recovery");
        assertThat(listed.method()).isEqualTo("POST");
        assertThat(resumed.target()).isEqualTo("/api/recovery/2/resume");
        assertThat(resumed.body()).isEqualTo("{}");
        assertThat(fresh.target()).isEqualTo("/api/recovery/4/fresh");
        assertThat(fresh.body()).isEqualTo("{\"effort\":\"medium\"}");
        assertThat(dropped.target()).isEqualTo("/api/recovery/5/drop");
        assertThat(dropped.body()).isEqualTo("{\"discardWork\":true}");
        assertThat(incomplete).isEqualTo(2);
        assertThat(err.toString()).contains("Name resume, fresh or drop after number 3, as the captain did.");
    }

    @Test
    void relaunchesAndStopsATasksWorker() {
        core.answering(200, "ok\n");

        edi("", "worker", "relaunch", "fit-lamp");
        StubCore.Request relaunched = core.received();
        edi("", "worker", "stop", "fit-lamp");

        assertThat(relaunched.target()).isEqualTo("/api/tasks/fit-lamp/relaunch");
        assertThat(relaunched.body()).isEqualTo("{}");
        assertThat(core.received().target()).isEqualTo("/api/tasks/fit-lamp/stop");
        assertThat(core.received().method()).isEqualTo("POST");
    }

    @Test
    void relaunchesATasksWorkerAfreshOnTheCaptainsWord() {
        core.answering(200, "ok\n");

        edi("", "worker", "relaunch", "fit-lamp", "--fresh");

        StubCore.Request relaunched = core.received();
        assertThat(relaunched.target()).isEqualTo("/api/tasks/fit-lamp/relaunch");
        assertThat(relaunched.body()).isEqualTo("{\"fresh\":true}");
    }

    private int edi(String input, String... arguments) {
        return new CommandLine(new Edi(Map.copyOf(environment), new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }

    private void inAWorkersWindowWithoutItsTokenFile() {
        environment.put("EDI_ROLE", "worker");
        environment.put("EDI_TASK_ID", "fit-lamp");
        environment.put("EDI_WORKER", "fit-lamp#1");
    }
}
