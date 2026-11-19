package io.github.ythibos.edi.application.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.browser.CaptainsBrowser;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.domain.primary.LaunchOrigin;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.primary.SessionStart;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.web.PresenterFixtures;

class OpenTheSitrepAtSessionStartTest {

    private static final URI CONSOLE = URI.create("http://127.0.0.1:5490/");
    private static final SessionSource LAUNCHED = SessionSource.LAUNCH;
    private static final SessionSource RESUMED = SessionSource.RESTART;
    private static final LaunchOrigin ELSEWHERE = LaunchOrigin.ELSEWHERE;
    private static final String NO_DESKTOP = "";
    private static final String CAPTAINS_FIRST_PROMPT = "/sitrep board";

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

        ConsoleOpening launch = sitrep.openFor(primary, started(primary, LAUNCHED), ELSEWHERE, CONSOLE).orElseThrow();

        assertThat(launch.opened()).isTrue();
        assertThat(launch.console()).isEqualTo(CONSOLE);
        assertThat(sitrep.openFor(primary, started(primary, RESUMED), ELSEWHERE, CONSOLE)).isEmpty();
        assertThat(sitrep.openFor(primary, started(primary, LAUNCHED), ELSEWHERE, CONSOLE)).isEmpty();
        assertThat(sitrep.openFor(standby, started(standby, LAUNCHED), ELSEWHERE, CONSOLE)).isEmpty();
        assertThat(Files.readAllLines(opened)).singleElement().asString().startsWith("/c start " + CONSOLE + "hand-off/");
    }

    @Test
    void opensNothingWhenTheMachineProfileSwitchesItOff() throws IOException {
        home.writing("config/machine.yaml", "console:\n  openSitrepAtSessionStart: false\n");
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID newcomer = UUID.randomUUID();

        assertThat(sitrep.openFor(newcomer, started(newcomer, LAUNCHED), ELSEWHERE, CONSOLE)).isEmpty();
        assertThat(opened).doesNotExist();
    }

    @Test
    void opensTheSitrepForAFreshEdiUpWhileADeadSessionStillHoldsThePrimaryRole() throws IOException {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID beforeTheOutage = UUID.randomUUID();
        UUID afterTheOutage = UUID.randomUUID();
        started(beforeTheOutage, LAUNCHED);

        SessionStart restart = started(afterTheOutage, LAUNCHED);
        ConsoleOpening opening = sitrep.openFor(afterTheOutage, restart, LaunchOrigin.EDI_UP, CONSOLE).orElseThrow();

        assertThat(restart.standing()).isEqualTo(Standing.STANDBY);
        assertThat(opening.opened()).isTrue();
        assertThat(Files.readAllLines(opened)).singleElement().asString().startsWith("/c start " + CONSOLE + "hand-off/");
    }

    @Test
    void opensNothingWhenEdiUpIsToldNotToEvenThoughTheProfileWantsTheBoard() throws IOException {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID newcomer = UUID.randomUUID();

        assertThat(sitrep.openFor(newcomer, started(newcomer, LAUNCHED), LaunchOrigin.EDI_UP_WITHOUT_THE_BOARD, CONSOLE)).isEmpty();
        assertThat(opened).doesNotExist();
    }

    @Test
    void opensNothingForAFreshEdiUpWhenTheMachineProfileSwitchesItOff() throws IOException {
        home.writing("config/machine.yaml", "console:\n  openSitrepAtSessionStart: false\n");
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID newcomer = UUID.randomUUID();

        assertThat(sitrep.openFor(newcomer, started(newcomer, LAUNCHED), LaunchOrigin.EDI_UP, CONSOLE)).isEmpty();
        assertThat(opened).doesNotExist();
    }

    @Test
    void opensNothingForTheResumeOfASessionEdiUpOpened() throws IOException {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID primary = UUID.randomUUID();

        sitrep.openFor(primary, started(primary, LAUNCHED), LaunchOrigin.EDI_UP, CONSOLE);

        assertThat(sitrep.openFor(primary, started(primary, RESUMED), LaunchOrigin.EDI_UP, CONSOLE)).isEmpty();
        assertThat(Files.readAllLines(opened)).hasSize(1);
    }

    @Test
    void opensASecondPageWhenTheCaptainsFirstPromptAsksForTheBoard() throws IOException {
        CaptainsBrowser browser = browserThatOpens();
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browser);
        OpenTheConsoleOnRequest console = home.openTheConsoleOnRequest(browser);
        UUID primary = UUID.randomUUID();

        sitrep.openFor(primary, started(primary, LAUNCHED), ELSEWHERE, CONSOLE);
        home.primaryHooks().promptSubmitted(primary, CAPTAINS_FIRST_PROMPT);
        ConsoleOpening firstTurn = console.openFor(Optional.of(primary), CONSOLE);

        assertThat(firstTurn.opened()).isTrue();
        assertThat(firstTurn.openedAtSessionStart()).isFalse();
        assertThat(Files.readAllLines(opened)).hasSize(2);
    }

    @Test
    void opensThePageOnRequestOnceTheSessionIsResumed() throws IOException {
        CaptainsBrowser browser = browserThatOpens();
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browser);
        OpenTheConsoleOnRequest console = home.openTheConsoleOnRequest(browser);
        UUID primary = UUID.randomUUID();

        sitrep.openFor(primary, started(primary, LAUNCHED), ELSEWHERE, CONSOLE);
        sitrep.openFor(primary, started(primary, RESUMED), ELSEWHERE, CONSOLE);
        ConsoleOpening afterTheResume = console.openFor(Optional.of(primary), CONSOLE);

        assertThat(afterTheResume.opened()).isTrue();
        assertThat(afterTheResume.openedAtSessionStart()).isFalse();
        assertThat(Files.readAllLines(opened)).hasSize(2);
    }

    @Test
    void opensThePageOnRequestWhenTheSessionsStartOpenedNone() throws IOException {
        home.writing("config/machine.yaml", "console:\n  openSitrepAtSessionStart: false\n");
        CaptainsBrowser browser = browserThatOpens();
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browser);
        OpenTheConsoleOnRequest console = home.openTheConsoleOnRequest(browser);
        UUID primary = UUID.randomUUID();

        sitrep.openFor(primary, started(primary, LAUNCHED), ELSEWHERE, CONSOLE);
        home.primaryHooks().promptSubmitted(primary, CAPTAINS_FIRST_PROMPT);
        ConsoleOpening fromTheSession = console.openFor(Optional.of(primary), CONSOLE);
        ConsoleOpening fromATerminal = console.openFor(Optional.empty(), CONSOLE);

        assertThat(fromTheSession.openedAtSessionStart()).isFalse();
        assertThat(fromATerminal.openedAtSessionStart()).isFalse();
        assertThat(Files.readAllLines(opened)).hasSize(2);
    }

    @Test
    void startsTheSessionWithTheConsolesAddressInTheDigestWhenTheBrowserCannotOpen() {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserBesideNothing());
        UUID primary = UUID.randomUUID();

        ConsoleOpening failed = sitrep.openFor(primary, started(primary, LAUNCHED), ELSEWHERE, CONSOLE).orElseThrow();

        assertThat(failed.opened()).isFalse();
        assertThat(digestTelling(primary, failed)).contains("\nYou could not open the console's sitrep page in the captain's browser; it is at " + CONSOLE
                + ", and edi console opens it signed in.\n");
    }

    @Test
    void tellsTheSessionTheStartOpenedNoSitrepAndHowToOpenIt() {
        UUID primary = UUID.randomUUID();
        started(primary, LAUNCHED);

        String digest = PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(primary, home.home().root()));

        assertThat(digest).contains("\nThis start did not open the console's sitrep page in the captain's browser; edi console opens it signed in.\n");
    }

    @Test
    void tellsTheSessionWhereTheSitrepOpened() throws IOException {
        OpenTheSitrepAtSessionStart sitrep = home.openTheSitrepAtSessionStart(browserThatOpens());
        UUID primary = UUID.randomUUID();

        ConsoleOpening launch = sitrep.openFor(primary, started(primary, LAUNCHED), ELSEWHERE, CONSOLE).orElseThrow();

        assertThat(digestTelling(primary, launch)).contains("\nThe console's sitrep page is open in the captain's browser at " + CONSOLE + ".\n");
    }

    private CaptainsBrowser browserThatOpens() throws IOException {
        Path browser = StandInScripts.installed(directory.resolve("bin/browser"), "#!/bin/sh\nprintf '%s\\n' \"$*\" >> '" + opened + "'\n");
        return new CaptainsBrowser(new ExternalPrograms(), home.home(), browser.toString(), Files.createDirectories(directory.resolve("windows")), NO_DESKTOP);
    }

    private SessionStart started(UUID session, SessionSource source) {
        return home.primaryHooks().sessionStarted(session, null, source);
    }

    private CaptainsBrowser browserBesideNothing() {
        return new CaptainsBrowser(new ExternalPrograms(), home.home(), "", directory.resolve("no-windows"), NO_DESKTOP);
    }

    private String digestTelling(UUID session, ConsoleOpening sitrep) {
        return PresenterFixtures.sessionStartDigestText().text(home.sessionStartDigest().digestFor(session, home.home().root()).withSitrep(sitrep));
    }
}
