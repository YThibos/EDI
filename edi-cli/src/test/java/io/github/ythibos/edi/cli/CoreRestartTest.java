package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

import picocli.CommandLine;

@Tag("adapter")
class CoreRestartTest {

    private static final Duration FINISHING_ITS_WORK = Duration.ofMillis(1500);
    private static final Duration NEVER_FINISHING = Duration.ofMinutes(5);
    private static final Duration SHORT_PATIENCE = Duration.ofSeconds(2);

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private Path home;
    private Path coreExecutable;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void installTheStandInCore() throws IOException, URISyntaxException {
        home = directory.resolve("home");
        coreExecutable = StandInScripts.installed(directory.resolve("bin/edi-core"), "#!/bin/sh\nexec '" + String.join("' '", standInCore(Duration.ZERO)) + "'\n");
    }

    @AfterEach
    void stopTheCoreTheCommandStarted() throws IOException {
        String recorded = CorePidFile.in(home).recordedProcess();
        if (!recorded.isEmpty()) {
            ProcessHandle.of(Long.parseLong(recorded)).ifPresent(core -> {
                core.destroy();
                TestProcesses.awaitExit(core);
            });
        }
    }

    @Test
    void updateStartsTheNewCoreOnlyOnceTheOldOneHasLetGoOfTheHome() throws Exception {
        cloneTheEdiRepository();
        long oldCore = runningCoreThatStopsWithin(FINISHING_ITS_WORK).pid();

        int updated = edi("update");

        assertThat(updated).as(err.toString()).isZero();
        assertThat(out.toString()).startsWith("EDI is updated to ");
        assertThat(Files.readString(home.resolve("state/logs/core.log"))).doesNotContain("Another EDI core runs on this home");
        assertThat(new LocalCore(home, coreExecutable.toString()).health()).isEqualTo(CoreHealth.UP);
        assertThat(CorePidFile.in(home).recordedProcess()).isNotEqualTo(Long.toString(oldCore));
    }

    @Test
    void downReturnsOnlyOnceTheCoreHasLetGoOfTheHome() throws Exception {
        runningCoreThatStopsWithin(FINISHING_ITS_WORK);

        int stopped = edi("down");

        assertThat(stopped).as(err.toString()).isZero();
        assertThat(CorePidFile.in(home).isHeld()).isFalse();
    }

    @Test
    void namesTheProcessThatStillHoldsTheHomeWhenItsPatienceRunsOut() throws Exception {
        long stuck = runningCoreThatStopsWithin(NEVER_FINISHING).pid();
        LocalCore core = new LocalCore(home, coreExecutable.toString(), SHORT_PATIENCE);

        assertThatThrownBy(core::awaitItsStop).isInstanceOf(CommandFailure.class).hasMessage("EDI core, process " + stuck + ", still holds "
                + home.resolve("state/core.pid") + " after " + SHORT_PATIENCE.toSeconds() + " seconds, so it has not stopped.");
    }

    private Process runningCoreThatStopsWithin(Duration stopping) throws IOException, URISyntaxException {
        ProcessBuilder launch = new ProcessBuilder(standInCore(stopping)).redirectErrorStream(true);
        launch.environment().put("EDI_HOME", home.toString());
        Process core = processes.register(launch.start());
        try (BufferedReader output = new BufferedReader(new InputStreamReader(core.getInputStream(), StandardCharsets.UTF_8))) {
            assertThat(output.readLine()).isEqualTo(StandInCore.UP);
        }
        return core;
    }

    private static String[] standInCore(Duration stopping) throws URISyntaxException {
        return new String[] {
            ProcessHandle.current().info().command().orElseThrow(),
            "-XX:TieredStopAtLevel=1",
            "-cp",
            Path.of(StandInCore.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
            StandInCore.class.getName(),
            Long.toString(stopping.toMillis())
        };
    }

    private int edi(String... arguments) {
        Map<String, String> environment = Map.of("EDI_HOME", home.toString(), "EDI_CORE", coreExecutable.toString(), "EDI_CHECKOUT",
                directory.resolve("EDI").toString());
        return new CommandLine(new Edi(environment, new ByteArrayInputStream(new byte[0])))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }

    private void cloneTheEdiRepository() throws IOException {
        FixtureRepository origin = FixtureRepository.initialise(Files.createDirectories(directory.resolve("origin")));
        StandInScripts.installed(origin.root().resolve("ops/install.sh"), "#!/bin/sh\nexit 0\n");
        origin.write("claude/primary/CLAUDE.md", "# The primary\n").commit("Install without building");
        origin.clonedInto(directory.resolve("EDI"));
    }
}
