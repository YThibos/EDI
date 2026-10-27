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

    private static final Instant TURN_END = Instant.parse("2026-10-26T19:31:00Z");

    private final PrimaryWaitText presenter = new PrimaryWaitText(new Moments(new ManualClock(TURN_END, ZoneId.of("Europe/Brussels"))));

    @Test
    void saysTheWaitIsLiveWhenItRanAtThePrimarysLastTurnEnd() {
        assertThat(presenter.text(new PrimaryWait(Sighting.LIVE, TURN_END, 2)))
                .isEqualTo("edi wait is live: it was running when the primary's last turn ended, at 2026-10-26 20:31 CET.\n");
    }

    @Test
    void saysNoWaitIsLiveAndHowMuchWorkIsInFlight() {
        assertThat(presenter.text(new PrimaryWait(Sighting.MISSING, TURN_END, 3)))
                .isEqualTo("No edi wait is live while 3 tasks are in flight: the primary's last turn ended at 2026-10-26 20:31 CET without one.\n");
        assertThat(presenter.text(new PrimaryWait(Sighting.MISSING, TURN_END, 1)))
                .isEqualTo("No edi wait is live while 1 task is in flight: the primary's last turn ended at 2026-10-26 20:31 CET without one.\n");
        assertThat(presenter.text(new PrimaryWait(Sighting.MISSING, TURN_END, 0)))
                .isEqualTo("No edi wait is live: the primary's last turn ended at 2026-10-26 20:31 CET without one.\n");
        assertThat(presenter.text(new PrimaryWait(Sighting.NO_PRIMARY, null, 2)))
                .isEqualTo("No edi wait is live while 2 tasks are in flight: no session holds the primary role.\n");
    }

    @Test
    void leavesTheCheckToTheStopWhileThePrimaryIsInATurn() {
        assertThat(presenter.text(new PrimaryWait(Sighting.UNCHECKED_IN_A_TURN, TURN_END, 2)))
                .isEqualTo("The primary is in a turn since 2026-10-26 20:31 CET; its stop checks that edi wait is live.\n");
    }
}
