package io.github.ythibos.edi.domain.wake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class WakeKindTest {

    @Test
    void namesAWorkersQuestionBlockAndDoneAsSettledByThePrimaryAndNothingThatSpeaksToTheCaptain() {
        assertThat(Arrays.stream(WakeKind.values()).filter(WakeKind::isSettledByThePrimary))
                .containsExactly(WakeKind.NEEDS_DECISION, WakeKind.BLOCKED, WakeKind.DONE);
    }
}
