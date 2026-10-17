package io.github.ythibos.edi.domain.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.presence.CalendarEntry.Response;
import io.github.ythibos.edi.domain.presence.CalendarEntry.Showing;

class CalendarEntryTest {

    private static final Instant START = Instant.parse("2026-10-17T10:00:00Z");
    private static final List<String> MARKERS = List.of("Example at the office", "Telework example");

    @Test
    void countsATimedBusyMeetingHeAcceptedOrOrganised() {
        for (Response his : List.of(Response.ACCEPTED, Response.ORGANISED, Response.OWN_APPOINTMENT)) {
            assertThat(entry("Sprint review", Duration.ofHours(1), false, his, Showing.BUSY).isAMeeting(MARKERS)).as(his.name()).isTrue();
        }
    }

    @Test
    void neverCountsAllDayLongFreeTentativeOrOutOfOfficeEntries() {
        assertThat(entry("Holiday", Duration.ofHours(24), true, Response.OWN_APPOINTMENT, Showing.BUSY).isAMeeting(MARKERS)).isFalse();
        assertThat(entry("Workshop", Duration.ofHours(4), false, Response.ACCEPTED, Showing.BUSY).isAMeeting(MARKERS)).isFalse();
        assertThat(entry("Coffee", Duration.ofHours(1), false, Response.ACCEPTED, Showing.FREE).isAMeeting(MARKERS)).isFalse();
        assertThat(entry("Maybe", Duration.ofHours(1), false, Response.TENTATIVE, Showing.TENTATIVE).isAMeeting(MARKERS)).isFalse();
        assertThat(entry("Leave", Duration.ofHours(1), false, Response.ACCEPTED, Showing.OUT_OF_OFFICE).isAMeeting(MARKERS)).isFalse();
        assertThat(entry("Declined", Duration.ofHours(1), false, Response.DECLINED, Showing.BUSY).isAMeeting(MARKERS)).isFalse();
    }

    @Test
    void neverCountsAPresenceMarkerTheSettingsList() {
        assertThat(entry("telework EXAMPLE", Duration.ofHours(1), false, Response.ACCEPTED, Showing.BUSY).isAMeeting(MARKERS)).isFalse();
    }

    @Test
    void isInProgressFromItsStartUntilItsEnd() {
        CalendarEntry meeting = entry("Sprint review", Duration.ofHours(1), false, Response.ACCEPTED, Showing.BUSY);

        assertThat(meeting.isAMeetingInProgressAt(START.minusSeconds(1), MARKERS)).isFalse();
        assertThat(meeting.isAMeetingInProgressAt(START, MARKERS)).isTrue();
        assertThat(meeting.isAMeetingInProgressAt(START.plus(Duration.ofHours(1)), MARKERS)).isFalse();
    }

    private static CalendarEntry entry(String subject, Duration length, boolean allDay, Response response, Showing showing) {
        return new CalendarEntry(subject, START, START.plus(length), allDay, response, showing);
    }
}
