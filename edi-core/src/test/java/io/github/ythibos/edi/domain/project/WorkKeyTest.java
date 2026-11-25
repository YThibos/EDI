package io.github.ythibos.edi.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkKeyTest {

    @Test
    void readsAProjectKeyAndANumber() {
        WorkKey key = WorkKey.parse("EDI-13");

        assertThat(key.project()).isEqualTo(new ProjectKey("EDI"));
        assertThat(key.number()).isEqualTo(13);
        assertThat(key).hasToString("EDI-13");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDI", "EDI-", "-3", "edi-3", "EDI-x", "EDI-0", "EDI-1234567890", "3EDI-3"})
    void refusesAnythingElse(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> WorkKey.parse(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDI-13", "EDI-13 Fit the lamp", "EDI-13: fit the lamp", "EDI-13, EDI-14 together"})
    void readsTheKeyThatOpensAText(String text) {
        assertThat(WorkKey.opening(text)).contains(WorkKey.parse("EDI-13"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Fit the lamp", "Merge EDI-13", "edi-13 fit", "EDI-1234567890 fit", "EDI-130x", "EDI-13a", "EDI- 13"})
    void readsNoKeyFromATextThatDoesNotOpenWithOne(String text) {
        assertThat(WorkKey.opening(text)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HarbourMaster", "tide-tables", "lamp-ci", "Front.UI_2"})
    void acceptsRegistryProjectNames(String name) {
        assertThat(new ProjectName(name).value()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-lead", "with space", "slash/name"})
    void refusesOtherProjectNames(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProjectName(name));
    }
}
