package io.github.ythibos.edi.web.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.testing.ManualClock;

class MomentsTest {

    private static final Instant SUMMER_TIME_ENDS = Instant.parse("2026-10-25T01:00:00Z");
    private static final Instant LATE_EVENING_IN_UTC = Instant.parse("2026-10-22T23:30:00Z");

    @Test
    void showsAMomentInTheMachinesZoneOnEitherSideOfTheClocksGoingBack() {
        Moments inBrussels = in("Europe/Brussels");

        assertThat(inBrussels.minute(SUMMER_TIME_ENDS.minusSeconds(60))).isEqualTo("2026-10-25 02:59 CEST");
        assertThat(inBrussels.minute(SUMMER_TIME_ENDS)).isEqualTo("2026-10-25 02:00 CET");
        assertThat(inBrussels.minute(Instant.parse("2026-03-29T01:00:00Z"))).isEqualTo("2026-03-29 03:00 CEST");
    }

    @Test
    void showsAMomentInAZoneOutsideBrussels() {
        assertThat(in("Europe/London").minute(SUMMER_TIME_ENDS.minusSeconds(60))).isEqualTo("2026-10-25 01:59 BST");
        assertThat(in("Europe/London").minute(SUMMER_TIME_ENDS)).isEqualTo("2026-10-25 01:00 GMT");
        assertThat(in("Asia/Kolkata").minute(SUMMER_TIME_ENDS)).isEqualTo("2026-10-25 06:30 IST");
        assertThat(in("UTC").minute(SUMMER_TIME_ENDS)).isEqualTo("2026-10-25 01:00 UTC");
    }

    @Test
    void tellsTheDayAsTheMachinesCalendarHasIt() {
        assertThat(in("Europe/Brussels").day(LATE_EVENING_IN_UTC)).isEqualTo("2026-10-23");
        assertThat(in("America/New_York").day(LATE_EVENING_IN_UTC)).isEqualTo("2026-10-22");
    }

    private static Moments in(String zone) {
        return new Moments(new ManualClock(SUMMER_TIME_ENDS, ZoneId.of(zone)));
    }
}
