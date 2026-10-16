package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"fix-login", "edi-s1", "a", "lamp-2641-closeout"})
    void acceptsAKebabCaseSlug(String id) {
        assertThat(new TaskId(id).value()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Fix-login", "fix login", "-fix", "fix-", "fix--login", "fix_login", "D58x!",
        "a-very-long-task-identifier-that-goes-well-beyond-the-sixty-four-limit"})
    void refusesAnythingElse(String id) {
        assertThatIllegalArgumentException().isThrownBy(() -> new TaskId(id)).withMessageContaining("kebab-case slug");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "EDI slice S1 - Domain and store|edi-slice-s1-domain-and-store",
        "  Fix the login:  timeout!  |fix-the-login-timeout",
        "Café au lait|caf-au-lait",
        "Replace the lantern controller with the newer model from the catalogue|replace-the-lantern-controller-with-the"})
    void slugsATitleIntoAnIdOfWholeWords(String title, String slug) {
        assertThat(TaskId.sluggedFrom(title)).isEqualTo(new TaskId(slug));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "!!!", "éèê à ô"})
    void refusesATitleThatGivesNoId(String title) {
        assertThatIllegalArgumentException().isThrownBy(() -> TaskId.sluggedFrom(title)).withMessageContaining("gives no task id");
    }

    @Test
    void numbersAnIdThatIsTaken() {
        assertThat(new TaskId("fix-login").withSuffix(2)).isEqualTo(new TaskId("fix-login-2"));
    }
}
