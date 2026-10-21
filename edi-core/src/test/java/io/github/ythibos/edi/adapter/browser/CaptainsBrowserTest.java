package io.github.ythibos.edi.adapter.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.testing.StandInScripts;

@Tag("adapter")
class CaptainsBrowserTest {

    private static final URI SIGN_IN = URI.create("http://127.0.0.1:47318/login#token");
    private static final String NO_DESKTOP = "";

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
        assertThat(Files.readString(opened)).isEqualTo("/c start " + SIGN_IN + "\n");
    }

    @Test
    void opensThePageOnALinuxDesktopWithoutWindows() throws IOException {
        assertThat(browserWith(browser, directory.resolve("no-windows"), ":0").open(SIGN_IN)).isTrue();
        assertThat(Files.readString(opened)).isEqualTo(SIGN_IN + "\n");
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

    private CaptainsBrowser browserWith(Path executable, Path windowsDirectory, String desktopDisplay) {
        return new CaptainsBrowser(new ExternalPrograms(), new EdiHome(directory), executable.toString(), windowsDirectory, desktopDisplay);
    }
}
