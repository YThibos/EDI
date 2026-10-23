package io.github.ythibos.edi.application.personality;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.personality.Personality;
import io.github.ythibos.edi.domain.personality.PersonalityChange;
import io.github.ythibos.edi.testing.TestHome;

class ChangePersonalityTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = TestHome.in(directory).writing("config/personal.yaml", "identity:\n  address: Skipper\n");
    }

    @Test
    void keepsEveryChangeInThePersonalConfiguration() throws IOException {
        PersonalityInForce changed = home.changePersonality().change(PersonalityChange.parse(List.of("humour", "2", "formality", "10")));

        assertThat(changed.speaking()).isEqualTo(Personality.of(2, 10, 5, 4, 6, 5, 5, 3, 5));
        assertThat(changed.captains().isChosen()).isTrue();
        assertThat(home.personalityQuery().current()).isEqualTo(changed);
        assertThat(Files.readString(home.home().personalConfiguration())).startsWith("identity:\n  address: Skipper\nedi-personality:\n  humour: 2\n");
    }

    @Test
    void speaksWithTheDemoOverrideOnlyWhileTheDemoHolds() {
        home.changePersonality().change(PersonalityChange.parse(List.of("formality", "10")));

        home.changePresence().demo();
        PersonalityInForce duringTheDemo = home.personalityQuery().current();
        home.changePresence().demoOff();

        assertThat(duringTheDemo.demoHolds()).isTrue();
        assertThat(duringTheDemo.speaking()).isEqualTo(Personality.of(3, 7, 5, 4, 6, 5, 5, 3, 7));
        assertThat(home.personalityQuery().current().speaking()).isEqualTo(Personality.of(3, 10, 5, 4, 6, 5, 5, 3, 5));
    }
}
