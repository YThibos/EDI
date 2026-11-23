package io.github.ythibos.edi.domain.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenUsageTest {

    @Test
    void weighsInputAtOneCacheReadsAtATenthCacheWritesAtOneAndAQuarterAndOutputAtFive() {
        TokenUsage spent = new TokenUsage(1_000, 10_000, 400, 200);

        assertThat(spent.weighted()).isEqualTo(1_000 + 1_000 + 500 + 1_000);
        assertThat(spent.total()).isEqualTo(11_600);
    }

    @Test
    void roundsAWeightToTheNearestInputToken() {
        assertThat(new TokenUsage(0, 4, 0, 0).weighted()).isZero();
        assertThat(new TokenUsage(0, 5, 0, 0).weighted()).isEqualTo(1);
    }
}
