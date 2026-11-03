package io.github.ythibos.edi.adapter.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class CaptainsBrowserTest {

    private static final URI HAND_OFF = URI.create("http://127.0.0.1:5490/hand-off/harbour-pilot-code");
    private static final String NO_DESKTOP = "";
    private static final Duration SHORT_PATIENCE = Duration.ofMillis(500);
    private static final Duration PATIENCE = Duration.ofSeconds(15);

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private Path opened;
    private Path browser;

    @BeforeEach
    void installTheBrowserStandIn() throws IOException {
        opened = directory.resolve("opened-in-the-browser");
        browser = StandInScripts.installed(directory.resolve("bin/browser"), "#!/bin/sh\nprintf '%s\\n' \"$*\" > '" + opened + "'\n");
    }

    @Test
    void opensTheConsolesHttpAddressBesideWindowsSoWindowsDefaultWebBrowserOpensIt() throws IOException {
        Path windows = Files.createDirectories(directory.resolve("windows"));

        assertThat(browserWith(browser, windows, ":0").open(HAND_OFF)).isTrue();
        assertThat(Files.readString(opened)).isEqualTo("/c start http://127.0.0.1:5490/hand-off/harbour-pilot-code\n");
    }

    @Test
    void opensTheConsolesHttpAddressOnALinuxDesktopWithoutWindows() throws IOException {
        assertThat(browserWith(browser, directory.resolve("no-windows"), ":0").open(HAND_OFF)).isTrue();
        assertThat(Files.readString(opened)).isEqualTo("http://127.0.0.1:5490/hand-off/harbour-pilot-code\n");
    }

    @Test
    void opensNothingWithoutWindowsOrADesktop() {
        assertThat(browserWith(browser, directory.resolve("no-windows"), NO_DESKTOP).open(HAND_OFF)).isFalse();
        assertThat(opened).doesNotExist();
    }

    @Test
    void reportsABrowserThatFailedOrCouldNotRun() throws IOException {
        Path failing = StandInScripts.installed(directory.resolve("bin/failing-browser"), "#!/bin/sh\nexit 1\n");

        assertThat(browserWith(failing, directory.resolve("no-windows"), ":0").open(HAND_OFF)).isFalse();
        assertThat(browserWith(directory.resolve("bin/missing"), directory.resolve("no-windows"), ":0").open(HAND_OFF)).isFalse();
    }

    @Test
    void countsABrowserStillRunningAfterItsPatienceAsOpenedAndLeavesItRunning() throws IOException {
        Path foreground = StandInScripts.installed(directory.resolve("bin/foreground-browser"), "#!/bin/sh\nsleep 30\n");

        boolean opened = browserWith(foreground, directory.resolve("no-windows"), ":0", SHORT_PATIENCE).open(HAND_OFF);
        ProcessHandle browserProcess = childRunning(foreground);
        processes.register(browserProcess.pid());

        assertThat(opened).isTrue();
        assertThat(browserProcess.isAlive()).isTrue();
        List<ProcessHandle> tree = Stream.concat(Stream.of(browserProcess), browserProcess.descendants()).toList();
        tree.forEach(ProcessHandle::destroyForcibly);
        assertThat(tree).allMatch(TestProcesses::awaitExit);
    }

    private CaptainsBrowser browserWith(Path executable, Path windowsDirectory, String desktopDisplay) {
        return browserWith(executable, windowsDirectory, desktopDisplay, PATIENCE);
    }

    private CaptainsBrowser browserWith(Path executable, Path windowsDirectory, String desktopDisplay, Duration patience) {
        return new CaptainsBrowser(new ExternalPrograms(), new EdiHome(directory), executable.toString(), windowsDirectory, desktopDisplay, patience);
    }

    private static ProcessHandle childRunning(Path standIn) {
        return ProcessHandle.current().children()
                .filter(child -> child.info().commandLine().filter(command -> command.contains(standIn.toString())).isPresent())
                .findFirst()
                .orElseThrow();
    }
}
