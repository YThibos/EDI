package io.github.ythibos.edi.application.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.presence.PresenceCause;
import io.github.ythibos.edi.domain.presence.PresenceMode;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;
import io.github.ythibos.edi.testing.TestHome;

class MeetingCalendarTest {

    private static final Duration ROUND = Duration.ofSeconds(30);
    private static final String UNREADABLE_TOAST = "The calendar cannot be read | Meetings added from now on do not turn your presence quiet until"
            + " Outlook answers again.";

    @TempDir
    private Path directory;

    private TestHome home;
    private NoticeTheUnreadableCalendar unreadableCalendar;

    @BeforeEach
    void prepareAHomeReadingOutlook() throws IOException {
        home = TestHome.in(directory).writing("config/machine.yaml", "notify:\n  windowsToast: true\npresence:\n  calendar: outlook\n");
        unreadableCalendar = home.noticeTheUnreadableCalendar();
    }

    @Test
    void turnsQuietInTheRoundAMeetingStartsWhileReadingTheCalendarOnlyEveryTwoMinutes() throws IOException {
        Instant meetingStart = home.clock().now().plus(Duration.ofMinutes(5));
        home.windowsSide().showingTheCalendar(calendarWith("Sprint review", meetingStart, meetingStart.plus(Duration.ofMinutes(15))));

        Presence beforeTheMeeting = roundsUntil(meetingStart.minus(ROUND));
        Presence inTheMeeting = roundsUntil(meetingStart);

        assertThat(beforeTheMeeting.mode()).isEqualTo(PresenceMode.PRESENT);
        assertThat(inTheMeeting).extracting(Presence::mode, Presence::cause, Presence::since)
                .containsExactly(PresenceMode.QUIET, PresenceCause.MEETING, meetingStart);
        assertThat(home.windowsSide().calendarReads()).isEqualTo(3);
    }

    @Test
    void tellsTheCaptainOnceWhileQuietThatTheCalendarCannotBeRead() throws IOException {
        Instant firstFailure = home.clock().now();
        home.windowsSide().failingToReadTheCalendar("Outlook did not answer.");

        round();
        boolean toldWhilePresent = !home.windowsSide().toasts().isEmpty();
        home.changePresence().quiet();
        roundsUntil(home.clock().now().plus(Duration.ofMinutes(10)));

        assertThat(toldWhilePresent).isFalse();
        assertThat(home.meetingCalendar().unreadable()).map(UnreadableCalendar::since).contains(firstFailure);
        assertThat(home.meetingCalendar().unreadable()).map(UnreadableCalendar::reason).hasValueSatisfying(reason -> assertThat(reason)
                .endsWith(" exited with 1: Outlook did not answer."));
        assertThat(home.windowsSide().toasts()).containsExactly(UNREADABLE_TOAST);
    }

    @Test
    void tellsTheCaptainAgainOnlyWhenTheCalendarFailsAfterItCouldBeReadOnceMore() throws IOException {
        home.changePresence().quiet();
        home.windowsSide().failingToReadTheCalendar("Outlook did not answer.");
        roundsUntil(home.clock().now().plus(Duration.ofMinutes(4)));
        home.windowsSide().showingTheCalendar("[]");
        roundsUntil(home.clock().now().plus(Duration.ofMinutes(4)));
        boolean readableAgain = home.meetingCalendar().unreadable().isEmpty();
        home.windowsSide().failingToReadTheCalendar("Outlook is closed.");
        roundsUntil(home.clock().now().plus(Duration.ofMinutes(4)));

        assertThat(readableAgain).isTrue();
        assertThat(home.windowsSide().toasts()).containsExactly(UNREADABLE_TOAST, UNREADABLE_TOAST);
    }

    private Presence roundsUntil(Instant last) {
        round();
        while (home.clock().now().isBefore(last)) {
            home.clock().advance(ROUND);
            round();
        }
        return home.presenceQuery().current();
    }

    private void round() {
        home.followThePresence().follow();
        unreadableCalendar.notice();
    }

    private static String calendarWith(String subject, Instant start, Instant end) {
        return "[{\"Subject\":\"" + subject + "\",\"Start\":\"" + start + "\",\"End\":\"" + end
                + "\",\"AllDay\":false,\"ResponseStatus\":3,\"BusyStatus\":2}]";
    }
}
