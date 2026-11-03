package io.github.ythibos.edi.adapter.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.testing.Patience;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class CaptainsBrowserTest {

    private static final URI SIGN_IN = URI.create("http://127.0.0.1:5490/login#harbour-pilot-token");
    private static final String NO_DESKTOP = "";
    private static final Duration SHORT_PATIENCE = Duration.ofMillis(500);
    private static final Duration PATIENCE = Duration.ofSeconds(15);
    private static final Duration LINK_LIFETIME = Duration.ofMinutes(2);
    private static final Duration MOMENT = Duration.ofMillis(200);
    private static final String DISTRIBUTION = "Harbour-Linux";

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
    void opensThePageInTheWindowsBrowserBesideWindows() throws IOException {
        Path windows = Files.createDirectories(directory.resolve("windows"));

        assertThat(browserWith(browser, windows, ":0").open(SIGN_IN)).isTrue();
        assertThat(Files.readString(opened)).isEqualTo("/c start file://wsl.localhost/" + DISTRIBUTION + onlyHandOffPage() + "\n");
        assertThat(Files.readString(onlyHandOffPage())).contains("<meta http-equiv=\"refresh\" content=\"0;url=" + SIGN_IN + "\">");
    }

    @Test
    void opensThePageOnALinuxDesktopWithoutWindows() throws IOException {
        assertThat(browserWith(browser, directory.resolve("no-windows"), ":0").open(SIGN_IN)).isTrue();
        assertThat(Files.readString(opened)).isEqualTo(onlyHandOffPage() + "\n");
        assertThat(Files.readString(onlyHandOffPage())).contains("<meta http-equiv=\"refresh\" content=\"0;url=" + SIGN_IN + "\">");
    }

    @Test
    void keepsTheHandOffPageForTheCaptainAlone() throws IOException {
        browserWith(browser, directory.resolve("no-windows"), ":0").open(SIGN_IN);

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(onlyHandOffPage()))).isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(onlyHandOffPage().getParent()))).isEqualTo("rwx------");
    }

    @Test
    void removesTheHandOffPageOnceTheLinkCouldNoLongerSignIn() throws Exception {
        new CaptainsBrowser(new ExternalPrograms(), new EdiHome(directory), browser.toString(), directory.resolve("no-windows"), ":0", DISTRIBUTION,
                MOMENT, PATIENCE).open(SIGN_IN);

        assertThat(removedWithinPatience(Path.of(Files.readString(opened).strip()))).isTrue();
    }

    @Test
    void opensNothingBesideWindowsWithoutTheNameOfItsLinuxDistribution() throws IOException {
        Path windows = Files.createDirectories(directory.resolve("windows"));

        assertThat(new CaptainsBrowser(new ExternalPrograms(), new EdiHome(directory), browser.toString(), windows, ":0", "", LINK_LIFETIME, PATIENCE)
                .open(SIGN_IN)).isFalse();
        assertThat(opened).doesNotExist();
    }

    @Test
    void handsTheSignInLinkToTheBrowserWithoutPuttingItOnACommandLine() throws IOException {
        Path windows = Files.createDirectories(directory.resolve("windows"));

        browserWith(browser, windows, ":0").open(SIGN_IN);
        String besideWindows = Files.readString(opened);
        browserWith(browser, directory.resolve("no-windows"), ":0").open(SIGN_IN);

        assertThat(besideWindows).doesNotContain(SIGN_IN.getFragment());
        assertThat(Files.readString(opened)).doesNotContain(SIGN_IN.getFragment());
    }

    @Test
    void opensNothingWithoutWindowsOrADesktop() {
        assertThat(browserWith(browser, directory.resolve("no-windows"), NO_DESKTOP).open(SIGN_IN)).isFalse();
        assertThat(opened).doesNotExist();
    }

    @Test
    void reportsABrowserThatFailedOrCouldNotRun() throws IOException {
        Path failing = StandInScripts.installed(directory.resolve("bin/failing-browser"), "#!/bin/sh\nexit 1\n");

        assertThat(browserWith(failing, directory.resolve("no-windows"), ":0").open(SIGN_IN)).isFalse();
        assertThat(browserWith(directory.resolve("bin/missing"), directory.resolve("no-windows"), ":0").open(SIGN_IN)).isFalse();
    }

    @Test
    void countsABrowserStillRunningAfterItsPatienceAsOpenedAndLeavesItRunning() throws IOException {
        Path foreground = StandInScripts.installed(directory.resolve("bin/foreground-browser"), "#!/bin/sh\nsleep 30\n");

        boolean opened = browserWith(foreground, directory.resolve("no-windows"), ":0", SHORT_PATIENCE).open(SIGN_IN);
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
        return new CaptainsBrowser(new ExternalPrograms(), new EdiHome(directory), executable.toString(), windowsDirectory, desktopDisplay,
                DISTRIBUTION, LINK_LIFETIME, patience);
    }

    private Path onlyHandOffPage() throws IOException {
        try (Stream<Path> pages = Files.list(new EdiHome(directory).consoleHandOff())) {
            return pages.reduce((first, second) -> {
                throw new IllegalStateException("More than one hand-off page: " + first + ", " + second);
            }).orElseThrow();
        }
    }

    private static boolean removedWithinPatience(Path handOff) throws IOException, InterruptedException {
        try (WatchService watcher = handOff.getFileSystem().newWatchService()) {
            handOff.getParent().register(watcher, StandardWatchEventKinds.ENTRY_DELETE);
            while (Files.exists(handOff)) {
                WatchKey removal = watcher.poll(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS);
                if (removal == null) {
                    return false;
                }
                removal.pollEvents();
                removal.reset();
            }
            return true;
        }
    }

    private static ProcessHandle childRunning(Path standIn) {
        return ProcessHandle.current().children()
                .filter(child -> child.info().commandLine().filter(command -> command.contains(standIn.toString())).isPresent())
                .findFirst()
                .orElseThrow();
    }
}
