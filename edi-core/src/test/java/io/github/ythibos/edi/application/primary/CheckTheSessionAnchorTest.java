package io.github.ythibos.edi.application.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.primary.HeldAnchor;
import io.github.ythibos.edi.testing.TestHome;

class CheckTheSessionAnchorTest {

    private static final String OLDER_STYLE = "---\nname: edi-primary\n---\n\n# EDI: the primary's anchor\n\nYou are an older E.D.I.\n";

    @TempDir
    private Path directory;

    @Test
    void findsTheCurrentAnchorInASessionStartedInTheHomeAfterEdiUpWroteIt() throws IOException {
        TestHome home = preparedHome();

        SessionAnchor anchor = checkIn(home).forSessionStartedIn(home.home().root());

        assertThat(anchor.held()).isEqualTo(HeldAnchor.CURRENT);
        assertThat(anchor.heading()).isEqualTo("# EDI: the primary's anchor");
    }

    @Test
    void findsNoAnchorInASessionStartedOutsideTheHome() throws IOException {
        TestHome home = preparedHome();
        Path elsewhere = Files.createDirectories(directory.resolve("elsewhere"));

        SessionAnchor anchor = checkIn(home).forSessionStartedIn(elsewhere);

        assertThat(anchor.held()).isEqualTo(HeldAnchor.NONE);
        assertThat(anchor.outputStyle()).isEqualTo(home.home().root().resolve(".claude/output-styles/edi-primary.md"));
    }

    @Test
    void findsNoAnchorWhenNoOutputStyleIsWritten() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"));

        assertThat(checkIn(home).forSessionStartedIn(home.home().root()).held()).isEqualTo(HeldAnchor.NONE);
    }

    @Test
    void findsNoAnchorWhenTheOutputStyleCarriesNoAnchorHeading() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home")).writing(".claude/output-styles/edi-primary.md", "---\nname: edi-primary\n---\n\n# Notes\n");

        assertThat(checkIn(home).forSessionStartedIn(home.home().root()).held()).isEqualTo(HeldAnchor.NONE);
    }

    @Test
    void findsAnOlderAnchorWhenTheOutputStyleCarriesTheHeadingButNotTheCoresAnchor() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home")).writing(".claude/output-styles/edi-primary.md", OLDER_STYLE);

        assertThat(checkIn(home).forSessionStartedIn(home.home().root()).held()).isEqualTo(HeldAnchor.OLDER);
    }

    @Test
    void findsNoAnchorWhenClaudeCodeNamesNoProjectDirectory() throws IOException {
        TestHome home = preparedHome();

        assertThat(checkIn(home).forSessionStartedIn(null).held()).isEqualTo(HeldAnchor.NONE);
    }

    @Test
    void pointsAtTheCheckoutsAnchorOnlyWhenTheCheckoutHoldsOne() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"));
        Path anchor = Files.createDirectories(checkout().resolve("claude/primary")).resolve("CLAUDE.md");

        SessionAnchor withoutIt = checkIn(home).forSessionStartedIn(home.home().root());
        Files.writeString(anchor, "# EDI: the primary's anchor\n");
        SessionAnchor withIt = checkIn(home).forSessionStartedIn(home.home().root());

        assertThat(withoutIt.currentAnchor()).isEmpty();
        assertThat(withIt.currentAnchor()).contains(anchor);
    }

    private TestHome preparedHome() throws IOException {
        TestHome home = TestHome.in(directory.resolve("home"));
        home.prepareThePrimary(Files.createDirectories(checkout())).prepare();
        return home;
    }

    private Path checkout() {
        return directory.resolve("checkout");
    }

    private CheckTheSessionAnchor checkIn(TestHome home) {
        return new CheckTheSessionAnchor(home.home(), checkout());
    }
}
