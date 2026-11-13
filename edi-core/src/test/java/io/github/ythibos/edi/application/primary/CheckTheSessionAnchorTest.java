package io.github.ythibos.edi.application.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestHome;

class CheckTheSessionAnchorTest {

    @TempDir
    private Path directory;

    @Test
    void findsTheAnchorInASessionStartedInTheHomeAfterEdiUpWroteIt() throws IOException {
        TestHome home = preparedHome();

        assertThat(checkIn(home).forSessionStartedIn(home.home().root()).loaded()).isTrue();
    }

    @Test
    void missesTheAnchorInASessionStartedOutsideTheHome() throws IOException {
        TestHome home = preparedHome();
        Path elsewhere = Files.createDirectories(directory.resolve("elsewhere"));

        SessionAnchor anchor = checkIn(home).forSessionStartedIn(elsewhere);

        assertThat(anchor.loaded()).isFalse();
        assertThat(anchor.outputStyle()).isEqualTo(home.home().root().resolve(".claude/output-styles/edi-primary.md"));
    }

    @Test
    void missesTheAnchorWhenNoOutputStyleCarriesIt() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"));

        assertThat(checkIn(home).forSessionStartedIn(home.home().root()).loaded()).isFalse();
    }

    @Test
    void missesTheAnchorWhenTheOutputStyleCarriesAnOlderOne() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home")).writing(".claude/output-styles/edi-primary.md", "---\nname: edi-primary\n---\n\n# Anchor\n");

        assertThat(checkIn(home).forSessionStartedIn(home.home().root()).loaded()).isFalse();
    }

    @Test
    void missesTheAnchorWhenClaudeCodeNamesNoWorkingDirectory() throws IOException {
        TestHome home = preparedHome();

        assertThat(checkIn(home).forSessionStartedIn(null).loaded()).isFalse();
    }

    private TestHome preparedHome() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"));
        home.prepareThePrimary(Files.createDirectories(directory.resolve("checkout"))).prepare();
        return home;
    }

    private static CheckTheSessionAnchor checkIn(TestHome home) {
        return new CheckTheSessionAnchor(home.home());
    }
}
