package io.github.ythibos.edi.domain.statusline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionTokensTest {

    @Test
    void countsTheCacheHitRatioAsTheCachedShareOfTheLastCallsInputRoundedDown() {
        assertThat(new SessionTokens(58_414, 25_144, 33_165).cacheHitRatio()).hasValue(43);
        assertThat(new SessionTokens(9_000, 9_000, 0).cacheHitRatio()).hasValue(100);
        assertThat(new SessionTokens(9_000, 0, 9_000).cacheHitRatio()).hasValue(0);
    }

    @Test
    void hasNoCacheHitRatioBeforeTheFirstCall() {
        assertThat(new SessionTokens(0, 0, 0).cacheHitRatio()).isEmpty();
    }
}
