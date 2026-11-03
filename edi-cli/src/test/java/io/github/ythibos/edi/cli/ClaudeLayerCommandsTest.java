package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

@Tag("adapter")
class ClaudeLayerCommandsTest {

    @TempDir
    private Path home;

    private StubCore core;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home);
        core.answering(200, "ok\n");
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void recordsTheCaptainsWordOnATaskOrOnTheHome() {
        edi("", "captain", "said", "--task", "D58", "--text", "Discard the lamp's copy.");
        StubCore.Request onATask = core.received();
        edi("Read the token.\nThen tell me.", "captain", "said", "--text", "-");
        StubCore.Request onTheHome = core.received();

        assertThat(onATask.target()).isEqualTo("/api/captain/said");
        assertThat(onATask.body()).isEqualTo("{\"text\":\"Discard the lamp's copy.\",\"task\":\"D58\"}");
        assertThat(onTheHome.body()).isEqualTo("{\"text\":\"Read the token.\\nThen tell me.\"}");
    }

    @Test
    void recordsTheCaptainsWordFromAStoredMessageOrOneLineOfIt() {
        edi("", "captain", "said", "--task", "fit-lamp", "--message", "3");
        StubCore.Request wholeMessage = core.received();
        edi("", "captain", "said", "--message", "4", "--line", "2");
        StubCore.Request oneLine = core.received();

        assertThat(wholeMessage.body()).isEqualTo("{\"message\":3,\"task\":\"fit-lamp\"}");
        assertThat(oneLine.body()).isEqualTo("{\"message\":4,\"line\":2}");
    }

    @Test
    void refusesToCiteTheCaptainsWordsTwiceOrNotAtAllOrALineWithoutItsMessage() {
        int twice = edi("", "captain", "said", "--message", "3", "--text", "Drop the lamp.");
        int never = edi("", "captain", "said", "--task", "fit-lamp");
        int lineAlone = edi("", "captain", "said", "--line", "2", "--text", "Drop the lamp.");

        assertThat(List.of(twice, never, lineAlone)).containsOnly(2);
        assertThat(err.toString()).contains("Cite the captain's words with --message, or with --text, one of the two.",
                "--line cites one line of a message, so it takes --message.");
        assertThat(core.requests()).isEmpty();
    }

    @Test
    void listsTheCaptainsMessagesOfThisSession() {
        core.answering(200, "The captain's messages in this session (0)\n");

        edi("", "captain", "messages");

        assertThat(core.received().target()).isEqualTo("/api/captain/messages");
        assertThat(out.toString()).isEqualTo("The captain's messages in this session (0)\n");
    }

    @Test
    void recordsTheCaptainsWordOnATaskForEffortAboveHighOnlyWithItsTask() {
        int recorded = edi("", "captain", "said", "--task", "fit-lamp", "--for", "effort-above-high", "--text", "Fit the lamp at xhigh.");
        StubCore.Request forTheLamp = core.received();
        int refused = edi("", "captain", "said", "--for", "effort-above-high", "--text", "Everything at xhigh.");

        assertThat(recorded).isZero();
        assertThat(forTheLamp.body()).isEqualTo("{\"text\":\"Fit the lamp at xhigh.\",\"task\":\"fit-lamp\",\"for\":\"effort-above-high\"}");
        assertThat(refused).isEqualTo(2);
        assertThat(err.toString()).contains("A word for one act is on one task, so --for takes --task.");
        assertThat(core.requests()).containsExactly(forTheLamp);
    }

    @Test
    void promotesDropsAndDiscardsOnTheCaptainsWord() {
        edi("", "task", "promote", "survey-lamps", "--title", "Fit the lamps");
        StubCore.Request promoted = core.received();
        edi("", "task", "drop", "fit-door");
        StubCore.Request dropped = core.received();
        edi("", "task", "drop", "fit-hinge", "--discard-work");
        StubCore.Request droppedWithItsWork = core.received();
        edi("", "worktree", "discard", "fit-lamp");
        StubCore.Request discarded = core.received();

        assertThat(promoted.target()).isEqualTo("/api/tasks/survey-lamps/promote");
        assertThat(promoted.body()).isEqualTo("{\"title\":\"Fit the lamps\"}");
        assertThat(dropped.target()).isEqualTo("/api/tasks/fit-door/drop");
        assertThat(dropped.body()).isEqualTo("{}");
        assertThat(droppedWithItsWork.body()).isEqualTo("{\"discardWork\":true}");
        assertThat(discarded.target()).isEqualTo("/api/tasks/fit-lamp/discard-work");
    }

    @Test
    void failsTheMemoryCheckWithTheCoresFindings() {
        core.answering(409, "The memory check fails: run /stow to curate the memory.\n");

        int failed = edi("", "memory", "check");
        core.answering(200, "To curate (0)\n");
        int listed = edi("", "memory", "stow-list");

        assertThat(failed).isOne();
        assertThat(err).hasToString("The memory check fails: run /stow to curate the memory.\n");
        assertThat(listed).isZero();
        assertThat(core.received().target()).isEqualTo("/api/memory/stow-list");
    }

    @Test
    void showsAndSetsThePresence() {
        edi("", "presence");
        StubCore.Request shown = core.received();
        edi("", "presence", "quiet");
        StubCore.Request quiet = core.received();
        edi("Back by noon; carry on with the lamp.", "presence", "away", "--mandate", "-");
        StubCore.Request away = core.received();
        edi("", "presence", "demo", "off");
        StubCore.Request demoOff = core.received();
        int refused = edi("", "presence", "quiet", "off");

        assertThat(shown.method()).isEqualTo("GET");
        assertThat(shown.target()).isEqualTo("/api/presence");
        assertThat(quiet.body()).isEqualTo("{\"mode\":\"quiet\"}");
        assertThat(away.body()).isEqualTo("{\"mode\":\"away\",\"mandate\":\"Back by noon; carry on with the lamp.\"}");
        assertThat(demoOff.body()).isEqualTo("{\"mode\":\"demo-off\"}");
        assertThat(refused).isEqualTo(2);
        assertThat(err.toString()).contains("Only a demo is ended with off: edi presence demo off.");
    }

    @Test
    void showsAndChangesThePersonality() {
        edi("", "personality");
        StubCore.Request shown = core.received();
        edi("", "personality", "humour", "6", "formality", "8");
        StubCore.Request changed = core.received();
        edi("", "personality", "plain");
        StubCore.Request preset = core.received();

        assertThat(shown.method()).isEqualTo("GET");
        assertThat(shown.target()).isEqualTo("/api/personality");
        assertThat(changed.target()).isEqualTo("/api/personality");
        assertThat(changed.body()).isEqualTo("{\"change\":[\"humour\",\"6\",\"formality\",\"8\"]}");
        assertThat(preset.body()).isEqualTo("{\"change\":[\"plain\"]}");
    }

    @Test
    void staysUpWhenTheCoreRefusesToStop() {
        core.answering(409, "1 worker is running (fit-lamp#1), so the core stays up.\n");

        int refused = edi("", "down", "--force-stop-workers");

        assertThat(refused).isOne();
        assertThat(core.received().target()).isEqualTo("/api/core/stop");
        assertThat(core.received().body()).isEqualTo("{\"forceStopWorkers\":true}");
        assertThat(err).hasToString("1 worker is running (fit-lamp#1), so the core stays up.\n");
    }

    @Test
    void launchesThePrimaryWaitingForTheCaptainToType() {
        PrimaryLaunch launch = PrimaryLaunch.read("""
                session: edi-261017-0930
                directory: /home/example/.edi
                settings: /home/example/.edi/state/primary/settings.json
                system prompt: /home/example/.edi/state/primary/system-prompt.md
                """);

        assertThat(launch.session()).isEqualTo("edi-261017-0930");
        assertThat(launch.claude()).containsExactly("claude", "--settings", "/home/example/.edi/state/primary/settings.json",
                "--append-system-prompt-file", "/home/example/.edi/state/primary/system-prompt.md");
    }

    @Test
    void letsThePrimaryRunPlainEdiCommandsWhileTheCoreIsDown() throws IOException {
        core.stopAnswering();

        int plain = edi("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"edi up --core-only\"}}", "hook", "pre-tool-use");
        int other = edi("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"edi up; rm -rf /tmp/x\"}}", "hook", "pre-tool-use");
        int feedingAHook = edi("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"edi hook post-tool-use\"}}", "hook", "pre-tool-use");
        int forAWorker = new CommandLine(new Edi(Map.of("EDI_HOME", home.toString(), "EDI_TOKEN_FILE", home.resolve("state/core.token").toString()),
                new ByteArrayInputStream("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"edi status working\"}}".getBytes(StandardCharsets.UTF_8))))
                .setOut(new PrintWriter(out, true)).setErr(new PrintWriter(err, true)).execute("hook", "pre-tool-use");

        assertThat(plain).isZero();
        assertThat(other).isEqualTo(2);
        assertThat(feedingAHook).isEqualTo(2);
        assertThat(forAWorker).isEqualTo(2);
    }

    @Test
    void letsThePrimaryAskTheCaptainWhileTheCoreIsDownUnlessTheAnswersAreGiven() throws IOException {
        core.stopAnswering();
        String questions = "\"questions\":[{\"question\":\"Shall I bring the core back?\",\"options\":[{\"label\":\"Yes\"}]}]";

        int asked = edi("{\"tool_name\":\"AskUserQuestion\",\"tool_input\":{" + questions + "}}", "hook", "pre-tool-use");
        int answered = edi("{\"tool_name\":\"AskUserQuestion\",\"tool_input\":{" + questions
                + ",\"answers\":{\"Shall I bring the core back?\":\"Yes\"}}}", "hook", "pre-tool-use");
        int annotated = edi("{\"tool_name\":\"AskUserQuestion\",\"tool_input\":{" + questions
                + ",\"annotations\":{\"Shall I bring the core back?\":{\"notes\":\"drop fix-login\"}}}}", "hook", "pre-tool-use");

        assertThat(asked).isZero();
        assertThat(answered).isEqualTo(2);
        assertThat(annotated).isEqualTo(2);
    }

    @Test
    void refusesTheWakeStreamFromBashWhileTheCoreIsDownAndLetsOneWaitAloneThrough() throws IOException {
        core.stopAnswering();

        int stream = edi("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"edi wait --follow\",\"run_in_background\":true}}", "hook", "pre-tool-use");
        int oneWait = edi("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"edi wait\",\"run_in_background\":true}}", "hook", "pre-tool-use");

        assertThat(stream).isEqualTo(2);
        assertThat(oneWait).isZero();
    }

    @Test
    void passesTheStatusLinePayloadThroughAndPrintsTheCoresLine() {
        core.answering(200, "\u001b[38;2;200;200;200mOpus 5.5\u001b[0m\n");

        int shown = edi("{\"model\":{\"display_name\":\"Opus 5.5\"}}", "statusline");
        StubCore.Request request = core.received();
        core.answering(400, "A status line names its working directory in workspace.current_dir or cwd.\n");
        int refused = edi("{}", "statusline");

        assertThat(shown).isZero();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.target()).isEqualTo("/api/statusline");
        assertThat(request.accepted()).isEqualTo("text/plain");
        assertThat(request.body()).isEqualTo("{\"model\":{\"display_name\":\"Opus 5.5\"}}");
        assertThat(out).hasToString("\u001b[38;2;200;200;200mOpus 5.5\u001b[0m\n");
        assertThat(refused).isOne();
        assertThat(err).hasToString("The status line could not be written: A status line names its working directory in workspace.current_dir or cwd.\n");
    }

    @Test
    void showsWhyTheCoreIsNotAnsweringInTheStatusLine() throws IOException {
        core.stopAnswering();

        int shown = edi("{}", "statusline");

        assertThat(shown).isZero();
        assertThat(out.toString()).startsWith("EDI core is not answering on ");
        assertThat(err.toString()).isEmpty();
    }

    private int edi(String input, String... arguments) {
        return new CommandLine(new Edi(Map.of("EDI_HOME", home.toString()), new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }
}
