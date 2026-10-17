package io.github.ythibos.edi.domain.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class PresenceTest {

    private static final Instant TYPED = Instant.parse("2026-10-17T09:00:00Z");
    private static final Duration THRESHOLD = Duration.ofMinutes(15);
    private static final Presence PRESENT = Presence.presentSince(TYPED);

    @Test
    void turnsQuietOnceTheCaptainHasNotTypedForTheThreshold() {
        assertThat(PRESENT.quietAfterSilence(TYPED, THRESHOLD, TYPED.plus(Duration.ofMinutes(14)))).isEqualTo(PRESENT);
        assertThat(PRESENT.quietAfterSilence(TYPED, THRESHOLD, TYPED.plus(Duration.ofHours(3))))
                .isEqualTo(new Presence(PresenceMode.QUIET, TYPED.plus(THRESHOLD), PresenceCause.SILENCE, null, null));
    }

    @Test
    void neverTurnsSilenceIntoAway() {
        Presence quiet = PRESENT.quietAfterSilence(TYPED, THRESHOLD, TYPED.plus(THRESHOLD));

        assertThat(quiet.quietAfterSilence(TYPED, THRESHOLD, TYPED.plus(Duration.ofDays(365))).mode()).isEqualTo(PresenceMode.QUIET);
    }

    @Test
    void turnsQuietForAMeetingOnlyWhenTheCaptainHasNotTypedSinceItBegan() {
        Instant meeting = TYPED.plus(Duration.ofMinutes(5));

        assertThat(PRESENT.quietForAMeeting(meeting, TYPED, meeting.plusSeconds(30)))
                .extracting(Presence::mode, Presence::cause, Presence::since).containsExactly(PresenceMode.QUIET, PresenceCause.MEETING, meeting);
        assertThat(PRESENT.quietForAMeeting(meeting, meeting.plusSeconds(10), meeting.plusSeconds(30))).isEqualTo(PRESENT);
    }

    @Test
    void holdsPresentThroughADemo() {
        Presence demo = PRESENT.demo(TYPED, TYPED.plus(Duration.ofHours(1)));
        Instant during = TYPED.plus(Duration.ofMinutes(50));
        Instant after = TYPED.plus(Duration.ofMinutes(61));

        assertThat(demo.quietAfterSilence(TYPED, THRESHOLD, during)).isEqualTo(demo);
        assertThat(demo.quietForAMeeting(TYPED, TYPED, during)).isEqualTo(demo);
        assertThat(demo.demoEndedBy(during)).isEqualTo(demo);
        assertThat(demo.demoEndedBy(after).demoUntil()).isNull();
        assertThat(demo.demoOff().demoUntil()).isNull();
    }

    @Test
    void returnsToPresentWhenTheCaptainTypes() {
        Presence away = PRESENT.away(new Mandate("Carry on with the lamp.", 2), TYPED.plusSeconds(60));

        assertThat(away.keepsTheChatQuiet()).isTrue();
        assertThat(away.captainTyped(TYPED.plusSeconds(120))).isEqualTo(Presence.presentSince(TYPED.plusSeconds(120)));
        assertThat(PRESENT.captainTyped(TYPED.plusSeconds(120))).isEqualTo(PRESENT);
    }

    @Test
    void carriesAMandateExactlyWhenAway() {
        assertThatThrownBy(() -> new Presence(PresenceMode.AWAY, TYPED, PresenceCause.CAPTAIN, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Presence(PresenceMode.QUIET, TYPED, PresenceCause.CAPTAIN, new Mandate("words", 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Presence(PresenceMode.QUIET, TYPED, PresenceCause.CAPTAIN, null, TYPED.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Mandate("  ", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
