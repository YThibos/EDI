package io.github.ythibos.edi.adapter.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.status.CaptainAddress;
import io.github.ythibos.edi.testing.ExampleHome;

@Tag("adapter")
class PersonalConfigurationTest {

    @TempDir
    private Path home;

    @Test
    void readsTheAddressAndThePresenceOfTheExample() throws IOException {
        PersonalConfiguration example = configurationOf(ExampleHome.file("config/personal.yaml"));

        assertThat(example.address()).isEqualTo(CaptainAddress.DEFAULT);
        assertThat(example.presence()).isEqualTo(new PresenceSettings(Duration.ofMinutes(15), Duration.ofMinutes(30),
                List.of("Example at the office", "Example working from home")));
    }

    @Test
    void fallsBackToTheSettledPresenceWithoutAFile() {
        PersonalConfiguration absent = new PersonalConfiguration(new EdiHome(home));

        assertThat(absent.address()).isEqualTo(CaptainAddress.DEFAULT);
        assertThat(absent.presence()).isEqualTo(new PresenceSettings(PresenceSettings.AUTO_QUIET_AFTER, PresenceSettings.QUIET_BATCH,
                List.of()));
    }

    @Test
    void refusesAPresenceItCannotRead() throws IOException {
        assertThatThrownBy(() -> configurationOf("presence:\n  autoQuietAfter: soon\n").presence())
                .isInstanceOf(InvalidConfiguration.class).hasMessageContaining("which is not a duration such as PT15M");
        assertThatThrownBy(() -> configurationOf("presence:\n  autoQuietAfter: PT0S\n").presence())
                .isInstanceOf(InvalidConfiguration.class).hasMessageContaining("positive duration");
    }

    private PersonalConfiguration configurationOf(String content) throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/personal.yaml"), content);
        return new PersonalConfiguration(new EdiHome(home));
    }
}
