package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RevisionRangeTest {

    @ParameterizedTest
    @ValueSource(strings = {"origin/main..HEAD", "abc1234..def5678", "HEAD~3..HEAD"})
    void acceptsAGitRevisionExpression(String expression) {
        assertThat(new RevisionRange(expression)).hasToString(expression);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "--all", "-p", "main.. HEAD", "main..HEAD\n--all"})
    void refusesAnythingThatCouldBeReadAsOptions(String expression) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RevisionRange(expression));
    }
}
