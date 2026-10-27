package io.github.ythibos.edi.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;
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

    @Test
    void namesEachExcludedRevisionAsANegatedRevision() {
        RevisionRange outgoing = new RevisionRange("origin/main..def5678", List.of("abc1234"));

        assertThat(outgoing.revisions()).containsExactly("origin/main..def5678", "^abc1234");
        assertThat(outgoing).hasToString("origin/main..def5678 excluding abc1234");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "--all", "abc1234 --all"})
    void refusesAnExclusionThatCouldBeReadAsOptions(String exclusion) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RevisionRange("origin/main..HEAD", List.of(exclusion)));
    }
}
