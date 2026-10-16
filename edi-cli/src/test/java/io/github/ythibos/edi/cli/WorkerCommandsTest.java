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

    private int edi(String input, String... arguments) {
        return new CommandLine(new Edi(Map.copyOf(environment), new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }
}
