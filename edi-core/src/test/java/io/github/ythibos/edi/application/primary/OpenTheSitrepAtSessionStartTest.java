package io.github.ythibos.edi.application.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.domain.primary.SessionStart;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.primary.SessionStartDigestText;
import io.github.ythibos.edi.web.worker.WorkerListText;

class OpenTheSitrepAtSessionStartTest {

    private static final URI CONSOLE = URI.create("http://127.0.0.1:47318/");
    private static final boolean LAUNCHED = true;
    private static final boolean RESUMED = false;
    private static final String NO_DESKTOP = "";

    @TempDir
    private Path directory;

    private TestHome home;
    private Path opened;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory);
        opened = directory.resolve("opened-in-the-browser");
    }

    @Test
    void opensTheSitrepOnceForTheLaunchOfANewPrimaryAndNeverForItsResumeOrAStandby() throws IOException {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID primary = UUID.randomUUID();
        UUID standby = UUID.randomUUID();

        ConsoleOpening launch = sitrep.openFor(started(primary, LAUNCHED), CONSOLE).orElseThrow();

        assertThat(launch.opened()).isTrue();
        assertThat(launch.console()).isEqualTo(CONSOLE);
        assertThat(sitrep.openFor(started(primary, RESUMED), CONSOLE)).isEmpty();
        assertThat(sitrep.openFor(started(primary, LAUNCHED), CONSOLE)).isEmpty();
        assertThat(sitrep.openFor(started(standby, LAUNCHED), CONSOLE)).isEmpty();
        assertThat(Files.readAllLines(opened)).singleElement().asString().startsWith("/c start " + CONSOLE + "login#");
    }

    @Test
    void opensNothingWhenTheMachineProfileSwitchesItOff() throws IOException {
        home.writing("config/machine.yaml", "console:\n  openSitrepAtSessionStart: false\n");
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());

        assertThat(sitrep.openFor(started(UUID.randomUUID(), LAUNCHED), CONSOLE)).isEmpty();
        assertThat(opened).doesNotExist();
    }

    @Test
    void startsTheSessionWithTheConsolesAddressInTheDigestWhenTheBrowserCannotOpen() {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserBesideNothing());
        UUID primary = UUID.randomUUID();

        ConsoleOpening failed = sitrep.openFor(started(primary, LAUNCHED), CONSOLE).orElseThrow();

        assertThat(failed.opened()).isFalse();
        assertThat(digestTelling(primary, failed)).contains("\nEDI could not open the console's sitrep page in the captain's browser; it is at " + CONSOLE
                + ", and edi console opens it signed in.\n");
    }

    @Test
    void tellsTheSessionWhereTheSitrepOpened() throws IOException {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID primary = UUID.randomUUID();

        ConsoleOpening launch = sitrep.openFor(started(primary, LAUNCHED), CONSOLE).orElseThrow();

        assertThat(digestTelling(primary, launch)).contains("\nThe console's sitrep page is open in the captain's browser at " + CONSOLE + ".\n");
    }

    private CaptainsBrowser browserThatOpens() throws IOException {
        Path browser = StandInScripts.installed(directory.resolve("bin/browser"), "#!/bin/sh\nprintf '%s\\n' \"$*\" >> '" + opened + "'\n");
        return new CaptainsBrowser(new ExternalPrograms(), home.home(), browser.toString(), Files.createDirectories(directory.resolve("windows")),
                NO_DESKTOP);
    }

    private SessionStart started(UUID session, boolean launched) {
        return home.primaryHooks().sessionStarted(session, null, launched);
    }

    private CaptainsBrowser browserBesideNothing() {
        return new CaptainsBrowser(new ExternalPrograms(), home.home(), "", directory.resolve("no-windows"), NO_DESKTOP);
    }

    private String digestTelling(UUID session, ConsoleOpening sitrep) {
        return new SessionStartDigestText(new WorkerListText()).text(home.sessionStartDigest().digestFor(session).withSitrep(sitrep));
    }
}
