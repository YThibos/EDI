package io.github.ythibos.edi.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RuleTest {

    @Test
    void namesEveryRuleByAStableIdUnderItsArea() {
        assertThat(Arrays.stream(Rule.values()).map(Rule::id))
                .doesNotHaveDuplicates()
                .allMatch(id -> id.matches("[a-z]+\\.[a-z]+(-[a-z0-9]+)*"));
        assertThat(Rule.FORCED_PUSH.id()).isEqualTo("guard.forced-push");
        assertThat(Rule.COMMIT_WORK_KEY.id()).isEqualTo("commit.work-key");
        assertThat(Rule.TASK_HOLD.id()).isEqualTo("task.hold");
    }

    @Test
    void keepsTheGuardsOwnWordsForWhatItRefused() {
        assertThat(Rule.FORCED_PUSH.word()).isEqualTo("forced-push");
        assertThat(Rule.OUTSIDE_ISOLATED_COPY.word()).isEqualTo("outside-isolated-copy");
    }

    @Test
    void findsARuleByItsIdOnly() {
        assertThat(Rule.withId("guard.forced-push")).contains(Rule.FORCED_PUSH);
        assertThat(Rule.withId("forced-push")).isEmpty();
        assertThat(Rule.withId("guard.never-written")).isEmpty();
    }

    @Test
    void saysWhatEveryRuleProtectsInOneSentence() {
        assertThat(Rule.values()).allSatisfy(rule -> assertThat(rule.protects()).endsWith(".").doesNotContain("—"));
    }
}
