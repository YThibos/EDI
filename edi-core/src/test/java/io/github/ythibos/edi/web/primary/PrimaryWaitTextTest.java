package io.github.ythibos.edi.web.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.primary.PrimaryWait;
import io.github.ythibos.edi.domain.primary.PrimaryWait.Sighting;
import io.github.ythibos.edi.testing.ManualClock;
import io.github.ythibos.edi.web.text.Moments;

class PrimaryWaitTextTest {

    private static final Instant STARTED = Instant.parse("2026-10-26T19:31:00Z");

    private final PrimaryWaitText presenter = new PrimaryWaitText(new Moments(new ManualClock(STARTED, ZoneId.of("Europe/Brussels"))));

    @Test
    void saysTheWakeStreamIsLiveAndSinceWhen() {
        assertThat(presenter.text(new PrimaryWait(Sighting.FOLLOWING, STARTED, 2)))
                .isEqualTo("The wake stream is live: edi wait --follow has run since 2026-10-26 20:31 CET.\n");
        assertThat(presenter.text(new PrimaryWait(Sighting.WAITING_ONCE, STARTED, 2)))
                .isEqualTo("edi wait is live since 2026-10-26 20:31 CET and ends at the next wake.\n");
    }

    @Test
    void saysNoWaitIsLiveHowMuchWorkIsInFlightAndHowToStartOne() {
        assertThat(presenter.text(new PrimaryWait(Sighting.MISSING, null, 3)))
                .isEqualTo("No edi wait is live while 3 tasks are in flight: start edi wait --follow alone with the Monitor tool.\n");
        assertThat(presenter.text(new PrimaryWait(Sighting.MISSING, null, 1)))
                .isEqualTo("No edi wait is live while 1 task is in flight: start edi wait --follow alone with the Monitor tool.\n");
        assertThat(presenter.text(new PrimaryWait(Sighting.MISSING, null, 0))).isEqualTo("No edi wait is live, and no task is in flight.\n");
        assertThat(presenter.text(new PrimaryWait(Sighting.NO_PRIMARY, null, 2)))
                .isEqualTo("No edi wait is live while 2 tasks are in flight: no session holds the primary role.\n");
    }
}
