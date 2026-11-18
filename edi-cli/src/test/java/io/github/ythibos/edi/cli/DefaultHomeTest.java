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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultHomeTest {

    @TempDir
    private Path userHome;

    private StubCore coreOfTheDefaultHome;
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void runACoreOnTheDefaultHome() throws IOException {
        coreOfTheDefaultHome = StubCore.recordedIn(userHome.resolve(".edi"));
    }

    @AfterEach
    void stopTheCore() {
        coreOfTheDefaultHome.close();
    }

    @Test
    void refusesTheDefaultHomeUnderATestRunnerBeforeReachingItsCore() throws IOException {
        int refused = edi(Map.of("EDI_TEST_RUN", "surefire"), elsewhere(), "task", "add", "--kind", "captain", "--title", "Made up");

        assertThat(refused).isEqualTo(1);
        assertThat(err.toString()).isEqualTo("edi refuses to fall back to the default EDI home " + userHome.resolve(".edi")
                + ": it runs under a test runner, which sets EDI_TEST_RUN, where a fallback would reach the captain's own core and store."
                + " Set EDI_HOME to a throwaway home this run created.\n");
        assertThat(coreOfTheDefaultHome.requests()).isEmpty();
    }

    @Test
    void refusesTheDefaultHomeInsideAWorkersIsolatedCopyBeforeReachingItsCore() throws IOException {
        Path copy = Files.createDirectories(userHome.resolve(".edi/worktrees/harness/fit-lamp"));

        int refused = edi(Map.of(), copy, "presence", "present");

        assertThat(refused).isEqualTo(1);
        assertThat(err.toString()).contains("it runs inside a worker's isolated copy, " + copy, "Set EDI_HOME");
        assertThat(coreOfTheDefaultHome.requests()).isEmpty();
    }

    @Test
    void refusesTheDefaultHomeInAValidationsWorkingDirectoryBeforeReachingItsCore() throws IOException {
        Path validation = Files.createDirectories(userHome.resolve(".no-mistakes/worktrees/0f6c3b2a"));

        int refused = edi(Map.of(), validation, "hook", "session-start");

        assertThat(refused).isEqualTo(1);
        assertThat(err.toString()).contains("it runs in a validation's working directory, " + validation, "Set EDI_HOME");
        assertThat(coreOfTheDefaultHome.requests()).isEmpty();
    }

    @Test
    void refusesEveryToolCallItsGuardCannotCheckWhileTheDefaultHomeIsRefusedAndRecordsNothingThere() throws IOException {
        Path copy = Files.createDirectories(userHome.resolve(".edi/worktrees/harness/fit-lamp"));

        int guarded = ediReading("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls\"}}", Map.of(), copy, "hook", "pre-tool-use");

        assertThat(guarded).isEqualTo(2);
        assertThat(err.toString()).startsWith("The guard could not check this tool call, so it is refused: edi refuses to fall back to the default EDI home");
        assertThat(coreOfTheDefaultHome.requests()).isEmpty();
        assertThat(RuleFirings.spoolIn(userHome.resolve(".edi"))).doesNotExist();
    }

    @Test
    void reachesTheCoreOfTheHomeItIsGivenWhereverItRuns(@TempDir Path throwawayHome) throws IOException {
        Path copy = Files.createDirectories(userHome.resolve(".edi/worktrees/harness/fit-lamp"));
        try (StubCore throwawayCore = StubCore.recordedIn(throwawayHome)) {
            int listed = edi(Map.of("EDI_HOME", throwawayHome.toString(), "EDI_TEST_RUN", "surefire"), copy, "task", "list");

            assertThat(listed).isZero();
            assertThat(throwawayCore.requests()).hasSize(1);
            assertThat(coreOfTheDefaultHome.requests()).isEmpty();
        }
    }

    @Test
    void fallsBackToTheDefaultHomeInTheCaptainsOwnTerminal() throws IOException {
        int listed = edi(Map.of(), elsewhere(), "task", "list");

        assertThat(listed).isZero();
        assertThat(coreOfTheDefaultHome.requests()).hasSize(1);
    }

    @Test
    void carriesThePrimarysLaunchKeyToTheCore() throws IOException {
        edi(Map.of("EDI_PRIMARY_LAUNCH_KEY", "launch-key-of-the-primary"), elsewhere(), "task", "list");

        assertThat(coreOfTheDefaultHome.received().primaryLaunchKey()).isEqualTo("launch-key-of-the-primary");
    }

    private Path elsewhere() throws IOException {
        return Files.createDirectories(userHome.resolve("projects/harness"));
    }

    private int edi(Map<String, String> variables, Path workingDirectory, String... arguments) {
        return ediReading("", variables, workingDirectory, arguments);
    }

    private int ediReading(String input, Map<String, String> variables, Path workingDirectory, String... arguments) {
        Map<String, String> environment = new HashMap<>(variables);
        environment.put("HOME", userHome.toString());
        Edi edi = new Edi(environment, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), StandardOutput.OF_THIS_PROCESS, workingDirectory);
        return Edi.commandLine(edi).setOut(new PrintWriter(new StringWriter())).setErr(new PrintWriter(err, true)).execute(arguments);
    }
}
