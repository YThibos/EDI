package io.github.ythibos.edi.application.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestHome;

class ConsoleAccessTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private ConsoleAccess access;

    @BeforeEach
    void openTheHome() throws IOException {
        home = TestHome.in(directory);
        access = home.consoleAccess();
    }

    @Test
    void opensOneSessionPerSignInLinkAndKeepsOnlyDigests() {
        String link = access.issueSignInLink();

        String session = access.openSession(link).orElseThrow();

        assertThat(access.admits(session)).isTrue();
        assertThat(access.openSession(link)).isEmpty();
        assertThat(home.jdbc().sql("select digest from console_sign_in union all select digest from console_session").query(String.class).list())
                .hasSize(2)
                .doesNotContain(link, session);
    }

    @Test
    void refusesASignInLinkUsedAfterTwoMinutesAndASessionAfterTwelveHours() {
        String late = access.issueSignInLink();
        String session = access.openSession(access.issueSignInLink()).orElseThrow();

        home.clock().advance(Duration.ofMinutes(2));
        assertThat(access.openSession(late)).isEmpty();

        home.clock().advance(Duration.ofHours(12));
        assertThat(access.admits(session)).isFalse();
    }

    @Test
    void admitsNoCookieItNeverIssued() {
        assertThat(access.admits("made-up")).isFalse();
    }
}
