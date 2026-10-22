package io.github.ythibos.edi.adapter.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.primary.SessionProcess;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class ProcessTableTest {

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private final ProcessTable table = new ProcessTable();

    @Test
    void tellsARunningProcessFromOneThatEndedOrReusedItsId() throws IOException {
        Process running = processes.register(new ProcessBuilder("sleep", "30").start());
        SessionProcess identified = table.identify(running.pid()).orElseThrow();

        assertThat(table.isRunning(identified)).isTrue();
        assertThat(table.isRunning(new SessionProcess(running.pid(), identified.startedAt().minusSeconds(60)))).isFalse();
        assertThat(table.parentOf(running.pid())).hasValue(ProcessHandle.current().pid());

        running.destroyForcibly();
        TestProcesses.awaitExit(running.toHandle());

        assertThat(table.isRunning(identified)).isFalse();
    }

    @Test
    void recognisesARunningProcessWhoseStartTheBootTimeDriftShiftedEitherWay() throws IOException {
        Process running = processes.register(new ProcessBuilder("sleep", "30").start());
        SessionProcess identified = table.identify(running.pid()).orElseThrow();

        assertThat(table.isRunning(new SessionProcess(running.pid(), identified.startedAt().plusSeconds(1)))).isTrue();
        assertThat(table.isRunning(new SessionProcess(running.pid(), identified.startedAt().minusSeconds(1)))).isTrue();
        running.destroyForcibly();
        assertThat(TestProcesses.awaitExit(running.toHandle())).isTrue();
    }

    @Test
    void findsTheProcessesWorkingInADirectory() throws IOException {
        Path copy = Files.createDirectories(directory.resolve("worktrees/harness/fit-lamp"));
        Process inside = processes.register(new ProcessBuilder("sleep", "30").directory(copy.toFile()).start());

        assertThat(table.workingIn(copy)).containsExactly(inside.pid());
        assertThat(table.workingIn(Files.createDirectories(directory.resolve("worktrees/harness/tune-horn")))).isEmpty();
        inside.destroyForcibly();
        assertThat(TestProcesses.awaitExit(inside.toHandle())).isTrue();
    }

    @Test
    void stopsAProcessTreeGracefullyAndKillsWhatOutlivesTheGrace() throws IOException {
        Process polite = processes.register(new ProcessBuilder("sleep", "30").start());
        Process stubborn = processes.register(new ProcessBuilder("sh", "-c", "trap '' TERM; while :; do sleep 1; done").start());

        table.stopTree(polite.pid(), Duration.ofSeconds(10));
        table.stopTree(stubborn.pid(), Duration.ofMillis(200));

        assertThat(TestProcesses.awaitExit(polite.toHandle())).isTrue();
        assertThat(TestProcesses.awaitExit(stubborn.toHandle())).isTrue();
        table.close();
    }
}
