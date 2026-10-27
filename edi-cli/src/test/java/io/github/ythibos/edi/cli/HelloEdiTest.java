package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class HelloEdiTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final String EDI_NOTING_ITS_CALLS = """
            #!/bin/bash
            echo "edi $1 in ${PWD}" >> "${HOME}/calls"
            if [[ "$1" == update && "${UPDATE_EXIT:-0}" == interrupted ]]; then
              kill -INT "${PPID}" "$$"
            fi
            [[ "$1" == update ]] && exit "${UPDATE_EXIT:-0}"
            exit 0
            """;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private Path home;
    private Path programs;
    private Path elsewhere;
    private FixtureRepository origin;
    private FixtureRepository checkout;

    @BeforeEach
    void cloneTheEdiRepositoryAndStandInForItsPrograms() throws IOException {
        assumeTrue(InstalledPrograms.answers("git", "--version"), "git is not installed");
        home = Files.createDirectories(directory.resolve("home"));
        elsewhere = Files.createDirectories(directory.resolve("elsewhere"));
        origin = FixtureRepository.initialise(Files.createDirectories(directory.resolve("origin"))).write("README.md", "EDI\n").commit("Start EDI");
        checkout = origin.clonedInto(home.resolve("EDI"));
        programs = directory.resolve("programs");
        StandInScripts.installed(programs.resolve("helloedi"), Files.readString(REPOSITORY.resolve("ops/helloedi")));
        StandInScripts.installed(programs.resolve("edi"), EDI_NOTING_ITS_CALLS);
        StandInScripts.installed(programs.resolve("tmux"), "#!/bin/sh\n");
        for (String program : List.of("bash", "git")) {
            StandInScripts.installed(programs.resolve(program), "#!/bin/sh\nexec '" + onThePath(program) + "' \"$@\"\n");
        }
    }

    @Test
    void startsEdiWithoutUpdatingItWhenTheInstalledBuildIsOriginMain() throws IOException, InterruptedException {
        recordTheInstalledBuild(origin.head());

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).containsExactly("edi up in " + elsewhere);
        assertThat(launch.exitCode()).isZero();
    }

    @Test
    void updatesEdiFromItsCheckoutThenStartsItWhenOriginMainMovedOn() throws IOException, InterruptedException {
        String installed = origin.head();
        recordTheInstalledBuild(installed);
        String latest = origin.write("README.md", "EDI, moved on\n").commit("Move EDI on").head();

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).containsExactly("edi update in " + checkout.root(), "edi up in " + elsewhere);
        assertThat(launch.output()).contains("the installed EDI is built from " + installed.substring(0, 7) + ", origin/main is at " + latest.substring(0, 7));
    }

    @Test
    void updatesEdiWhenTheCheckoutIsPulledButTheInstalledBuildIsOlder() throws IOException, InterruptedException {
        recordTheInstalledBuild(origin.head());
        origin.write("README.md", "EDI, moved on\n").commit("Move EDI on");
        checkout.run("pull", "--quiet", "--ff-only");

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).containsExactly("edi update in " + checkout.root(), "edi up in " + elsewhere);
    }

    @Test
    void startsEdiWithoutUpdatingItWhenTheInstalledBuildIsAheadOfOriginMain() throws IOException, InterruptedException {
        recordTheInstalledBuild(checkout.branch("trial").write("README.md", "EDI, on trial\n").commit("Try EDI out").head());

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).containsExactly("edi up in " + elsewhere);
    }

    @Test
    void updatesEdiWhenTheInstalledBuildIsUnknownToTheCheckout() throws IOException, InterruptedException {
        recordTheInstalledBuild("0123456789abcdef0123456789abcdef01234567");

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).containsExactly("edi update in " + checkout.root(), "edi up in " + elsewhere);
        assertThat(launch.output()).contains("the installed EDI is built from 0123456");
    }

    @Test
    void updatesEdiWhenTheInstallRecordsNoBuild() throws IOException, InterruptedException {
        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).containsExactly("edi update in " + checkout.root(), "edi up in " + elsewhere);
        assertThat(launch.output()).contains("the installed EDI records no build commit, origin/main is at " + origin.head().substring(0, 7));
    }

    @Test
    void startsTheInstalledEdiWhenTheUpdateFails() throws IOException, InterruptedException {
        Launch launch = helloedi(Map.of("UPDATE_EXIT", "1"));

        assertThat(launch.calls()).containsExactly("edi update in " + checkout.root(), "edi up in " + elsewhere);
        assertThat(launch.output()).contains("helloedi: edi update failed (exit 1), starting the installed EDI");
        assertThat(launch.exitCode()).isZero();
    }

    @Test
    void startsTheInstalledEdiWhenTheCheckoutCannotBeFetched() throws IOException, InterruptedException {
        checkout.configure("remote.origin.url", directory.resolve("gone").toString());

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).containsExactly("edi up in " + elsewhere);
        assertThat(launch.output()).contains("helloedi: could not fetch " + checkout.root() + ", starting the installed EDI");
    }

    @Test
    void stopsWithoutStartingEdiWhenCtrlCInterruptsTheUpdate() throws IOException, InterruptedException {
        Launch launch = helloedi(Map.of("UPDATE_EXIT", "interrupted"));

        assertThat(launch.calls()).containsExactly("edi update in " + checkout.root());
        assertThat(launch.exitCode()).isEqualTo(130);
    }

    @Test
    void stopsWithoutStartingEdiWhenTheUpdateEndsAsInterrupted() throws IOException, InterruptedException {
        Launch launch = helloedi(Map.of("UPDATE_EXIT", "130"));

        assertThat(launch.calls()).containsExactly("edi update in " + checkout.root());
        assertThat(launch.exitCode()).isEqualTo(130);
    }

    @Test
    void refusesWithoutTmux() throws IOException, InterruptedException {
        Files.delete(programs.resolve("tmux"));

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).isEmpty();
        assertThat(launch.output()).contains("helloedi: tmux is not installed. Install it with: sudo apt install tmux");
        assertThat(launch.exitCode()).isEqualTo(1);
    }

    @Test
    void refusesWithoutTheEdiCheckout() throws IOException, InterruptedException {
        Files.move(checkout.root(), home.resolve("EDI-moved"));

        Launch launch = helloedi(Map.of());

        assertThat(launch.calls()).isEmpty();
        assertThat(launch.output()).contains("helloedi: " + checkout.root() + " does not exist on this machine. Clone it with: git clone");
        assertThat(launch.exitCode()).isEqualTo(1);
    }

    @Test
    void refusesWithoutEdiOnThePath() throws IOException, InterruptedException {
        Files.delete(programs.resolve("edi"));

        Launch launch = helloedi(Map.of());

        assertThat(launch.output()).contains("helloedi: edi is not on PATH. Install it from " + checkout.root() + " with: ops/install.sh");
        assertThat(launch.exitCode()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bash", "zsh"})
    void startsFromTheCaptainsShell(String shell) throws IOException, InterruptedException {
        assumeTrue(InstalledPrograms.answers(shell, "--version"), shell + " is not installed");
        recordTheInstalledBuild(origin.head());

        Launch launch = launch(List.of(onThePath(shell).toString(), "-c", "helloedi"), Map.of());

        assertThat(launch.calls()).containsExactly("edi up in " + elsewhere);
        assertThat(launch.exitCode()).isZero();
    }

    private void recordTheInstalledBuild(String commit) throws IOException {
        Path record = Files.createDirectories(home.resolve(".edi/state")).resolve("installed-build");
        Files.writeString(record, commit + "\n");
    }

    private Launch helloedi(Map<String, String> environment) throws IOException, InterruptedException {
        return launch(List.of(programs.resolve("helloedi").toString()), environment);
    }

    private Launch launch(List<String> command, Map<String, String> environment) throws IOException, InterruptedException {
        ProcessBuilder launch = new ProcessBuilder(command).directory(elsewhere.toFile()).redirectErrorStream(true);
        launch.environment().clear();
        launch.environment().putAll(Map.of("HOME", home.toString(), "PATH", programs.toString(), "GIT_CONFIG_GLOBAL", "/dev/null", "GIT_CONFIG_NOSYSTEM", "1"));
        launch.environment().putAll(environment);
        Process helloedi = processes.register(launch.start());
        helloedi.getOutputStream().close();
        String output = new String(helloedi.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!helloedi.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
            helloedi.destroyForcibly();
            throw new IOException("helloedi did not finish within " + PATIENCE.toSeconds() + " seconds");
        }
        return new Launch(helloedi.exitValue(), output, callsToEdi());
    }

    private List<String> callsToEdi() throws IOException {
        Path calls = home.resolve("calls");
        return Files.exists(calls) ? Files.readAllLines(calls) : List.of();
    }

    private static Path onThePath(String program) {
        return Stream.of(System.getenv("PATH").split(File.pathSeparator)).map(entry -> Path.of(entry, program)).filter(Files::isExecutable)
                .findFirst().orElseThrow();
    }

    private record Launch(int exitCode, String output, List<String> calls) {
    }
}
