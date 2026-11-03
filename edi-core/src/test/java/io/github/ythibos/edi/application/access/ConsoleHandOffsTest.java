package io.github.ythibos.edi.application.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.testing.ManualClock;

class ConsoleHandOffsTest {

    private static final URI CONSOLE = URI.create("http://127.0.0.1:5490/");
    private static final URI SIGN_IN = URI.create("http://127.0.0.1:5490/login#harbour-pilot-token");

    private final ManualClock clock = new ManualClock(Instant.parse("2026-11-03T10:00:00Z"));
    private final ConsoleHandOffs handOffs = new ConsoleHandOffs(clock);

    @Test
    void handsOffThroughAnHttpAddressOfTheConsoleThatCarriesNoSignInToken() {
        URI handOff = handOffs.handOff(CONSOLE, SIGN_IN);

        assertThat(handOff.toString()).startsWith("http://127.0.0.1:5490/hand-off/").doesNotContain(SIGN_IN.getFragment());
        assertThat(handOffs.handOff(CONSOLE, SIGN_IN)).isNotEqualTo(handOff);
    }

    @Test
    void leadsOnToTheSignInLinkOnce() {
        String code = codeOf(handOffs.handOff(CONSOLE, SIGN_IN));

        assertThat(handOffs.follow(code)).contains(SIGN_IN);
        assertThat(handOffs.follow(code)).isEmpty();
    }

    @Test
    void leadsNowhereOnceTheSignInLinkCouldNoLongerSignIn() {
        String code = codeOf(handOffs.handOff(CONSOLE, SIGN_IN));

        clock.advance(Duration.ofMinutes(2));

        assertThat(handOffs.follow(code)).isEmpty();
    }

    @Test
    void leadsNowhereForACodeItNeverHandedOut() {
        assertThat(handOffs.follow("made-up")).isEmpty();
    }

    private static String codeOf(URI handOff) {
        return handOff.getPath().substring(handOff.getPath().lastIndexOf('/') + 1);
    }
}
