package io.github.ythibos.edi.adapter.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.personality.CaptainsPersonality;
import io.github.ythibos.edi.domain.personality.Level;
import io.github.ythibos.edi.domain.personality.Personality;
import io.github.ythibos.edi.domain.personality.PersonalityOverride;
import io.github.ythibos.edi.domain.personality.Preset;
import io.github.ythibos.edi.domain.personality.Scale;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.testing.ExampleHome;

@Tag("adapter")
class PersonalConfigurationTest {

    private static final String PLAIN_SECTION = """
            edi-personality:
              humour: 0
              formality: 5
              verbosity: 3
              warmth: 2
              directness: 7
              initiative: 3
              depth: 5
              enthusiasm: 0
              flavour: 0
              demo:
                humour: 3
                formality: 7
                verbosity: 5
                flavour: 7
            """;

    @TempDir
    private Path home;

    @Test
    void readsTheAddressAndThePresenceOfTheExample() throws IOException {
        PersonalConfiguration example = configurationOf(ExampleHome.file("config/personal.yaml"));

        assertThat(example.address()).isEqualTo(CaptainAddress.DEFAULT);
        assertThat(example.personality()).isEqualTo(new CaptainsPersonality(Optional.of(Preset.BALANCED.personality()), PersonalityOverride.SHIPPED_DEMO));
        assertThat(example.presence()).isEqualTo(new PresenceSettings(Duration.ofMinutes(15), Duration.ofMinutes(30),
                List.of("Example at the office", "Example working from home")));
    }

    @Test
    void fallsBackToTheSettledPresenceWithoutAFile() {
        PersonalConfiguration absent = new PersonalConfiguration(new EdiHome(home));

        assertThat(absent.address()).isEqualTo(CaptainAddress.DEFAULT);
        assertThat(absent.personality()).isEqualTo(CaptainsPersonality.UNCHOSEN);
        assertThat(absent.presence()).isEqualTo(new PresenceSettings(PresenceSettings.AUTO_QUIET_AFTER, PresenceSettings.QUIET_BATCH,
                List.of()));
    }

    @Test
    void refusesAPresenceItCannotRead() {
        assertThatThrownBy(() -> configurationOf("presence:\n  autoQuietAfter: soon\n").presence())
                .isInstanceOf(InvalidConfiguration.class).hasMessageContaining("which is not a duration such as PT15M");
        assertThatThrownBy(() -> configurationOf("presence:\n  autoQuietAfter: PT0S\n").presence())
                .isInstanceOf(InvalidConfiguration.class).hasMessageContaining("positive duration");
    }

    @Test
    void writesTheAddressWordOnlyIntoAHomeWithoutAConfiguration() throws IOException {
        assertThat(new PersonalConfiguration(new EdiHome(home)).contentAddressing(CaptainAddress.DEFAULT)).isEqualTo("identity:\n  address: Commander\n");
        assertThat(configurationOf("presence:\n  quietBatch: PT10M\n").contentAddressing(CaptainAddress.DEFAULT))
                .isEqualTo("presence:\n  quietBatch: PT10M\n");
    }

    @Test
    void readsAPersonalityThatNamesSomeScalesOnTopOfBalanced() throws IOException {
        CaptainsPersonality personality = configurationOf("edi-personality:\n  humour: 2\n  formality: 10\n  demo:\n    humour: 4\n").personality();

        assertThat(personality.own()).isEqualTo(Personality.of(2, 10, 5, 4, 6, 5, 5, 3, 5));
        assertThat(personality.demo()).isEqualTo(new PersonalityOverride(Map.of(Scale.HUMOUR, Level.of(4))));
    }

    @Test
    void refusesAPersonalityItCannotRead() {
        assertThatThrownBy(() -> configurationOf("edi-personality:\n  humour: 11\n").personality())
                .isInstanceOf(InvalidConfiguration.class).hasMessageEndingWith("edi-personality.humour holds 11, which is not a level from 0 to 10.");
        assertThatThrownBy(() -> configurationOf("edi-personality:\n  humour: dry\n").personality())
                .isInstanceOf(InvalidConfiguration.class).hasMessageEndingWith("edi-personality.humour holds \"dry\", which is not a level from 0 to 10.");
        assertThatThrownBy(() -> configurationOf("edi-personality:\n  humor: 2\n").personality())
                .isInstanceOf(InvalidConfiguration.class).hasMessageContaining("humor is not a personality scale");
        assertThatThrownBy(() -> configurationOf("edi-personality:\n  humour: 3\n  Humour: 7\n").personality())
                .isInstanceOf(InvalidConfiguration.class).hasMessageEndingWith("edi-personality names humour twice; name each scale once.");
        assertThatThrownBy(() -> configurationOf("edi-personality:\n  demo:\n    formality: 7\n    Formality: 2\n").personality())
                .isInstanceOf(InvalidConfiguration.class).hasMessageEndingWith("edi-personality.demo names formality twice; name each scale once.");
        assertThatThrownBy(() -> configurationOf("edi-personality: lively\n").personality())
                .isInstanceOf(InvalidConfiguration.class).hasMessageEndingWith("edi-personality holds the personality's scales and their levels, such as humour: 3.");
        assertThatThrownBy(() -> configurationOf("edi-personality:\n  demo: 7\n").personality())
                .isInstanceOf(InvalidConfiguration.class).hasMessageEndingWith("edi-personality.demo holds the scales a demo overrides, such as formality: 7.");
    }

    @Test
    void writesTheChosenPersonalityIntoAHomeWithoutAConfiguration() throws IOException {
        PersonalConfiguration absent = new PersonalConfiguration(new EdiHome(home));

        absent.choose(CaptainsPersonality.UNCHOSEN.changedBy(Preset.PLAIN));

        assertThat(Files.readString(home.resolve("config/personal.yaml"))).isEqualTo(PLAIN_SECTION);
        assertThat(absent.personality().own()).isEqualTo(Preset.PLAIN.personality());
    }

    @Test
    void appendsThePersonalityAndKeepsEverythingElseAsWritten() throws IOException {
        String written = "# The captain's own settings\nidentity:\n  address: Skipper # short\npresence:\n  quietBatch: PT10M";
        PersonalConfiguration configuration = configurationOf(written);

        configuration.choose(CaptainsPersonality.UNCHOSEN.changedBy(Preset.PLAIN));

        assertThat(Files.readString(home.resolve("config/personal.yaml"))).isEqualTo(written + "\n" + PLAIN_SECTION);
    }

    @Test
    void replacesThePersonalityWhereItStandsAndKeepsTheRestAsWritten() throws IOException {
        String before = "identity:\n  address: Skipper # short\n";
        String after = "\n# Presence follows\npresence:\n  quietBatch: PT10M\n";
        PersonalConfiguration configuration = configurationOf(before + "edi-personality:\n  # dry, please\n  humour: 2\n" + after);

        configuration.choose(configuration.personality().changedBy(Preset.PLAIN));

        assertThat(Files.readString(home.resolve("config/personal.yaml"))).isEqualTo(before + PLAIN_SECTION + after);
    }

    @Test
    void rewritesTheWholeConfigurationWhenThePersonalityIsNotWrittenAsABlock() throws IOException {
        PersonalConfiguration configuration = configurationOf("identity:\n  address: Skipper\nedi-personality: {humour: 2}\n");

        configuration.choose(configuration.personality().changedBy(Preset.PLAIN));

        assertThat(Files.readString(home.resolve("config/personal.yaml"))).isEqualTo("identity:\n  address: Skipper\n" + PLAIN_SECTION);
    }

    private PersonalConfiguration configurationOf(String content) throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/personal.yaml"), content);
        return new PersonalConfiguration(new EdiHome(home));
    }
}
