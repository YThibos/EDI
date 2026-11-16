package io.github.ythibos.edi.domain.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class WeeklyGardeningTest {

    private static final LocalDateTime MONDAY_AT_THE_RESET = LocalDateTime.of(2026, 11, 16, 9, 0);

    @Test
    void isDueOnMondayFromTheWeeklyUsageResetOn() {
        assertThat(WeeklyGardening.isDueAt(MONDAY_AT_THE_RESET)).isTrue();
        assertThat(WeeklyGardening.isDueAt(MONDAY_AT_THE_RESET.withHour(23).withMinute(59))).isTrue();
    }

    @Test
    void isNotDueBeforeTheResetNorOnAnyOtherDay() {
        assertThat(WeeklyGardening.isDueAt(MONDAY_AT_THE_RESET.minusMinutes(1))).isFalse();
        assertThat(WeeklyGardening.isDueAt(MONDAY_AT_THE_RESET.minusDays(1))).isFalse();
        assertThat(WeeklyGardening.isDueAt(MONDAY_AT_THE_RESET.plusDays(1))).isFalse();
    }
}
