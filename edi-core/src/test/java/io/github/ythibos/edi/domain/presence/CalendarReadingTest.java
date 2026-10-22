package io.github.ythibos.edi.domain.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.presence.CalendarEntry.Response;
import io.github.ythibos.edi.domain.presence.CalendarEntry.Showing;

class CalendarReadingTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);
    private static final Instant READ_AT = Instant.parse("2026-10-22T09:00:00Z");
    private static final CalendarEntry SPRINT_REVIEW = new CalendarEntry("Sprint review", READ_AT.plus(Duration.ofMinutes(50)),
            READ_AT.plus(Duration.ofMinutes(65)), false, Response.ACCEPTED, Showing.BUSY);

    @Test
    void isReadAgainAfterTwoMinutesAndOnANewDay() {
        CalendarReading reading = CalendarReading.of(DAY, READ_AT, List.of(SPRINT_REVIEW));

        assertThat(CalendarReading.NOT_YET_READ.isDueAt(DAY, READ_AT)).isTrue();
        assertThat(reading.isDueAt(DAY, READ_AT.plus(Duration.ofSeconds(119)))).isFalse();
        assertThat(reading.isDueAt(DAY, READ_AT.plus(CalendarReading.READ_AGAIN_AFTER))).isTrue();
        assertThat(reading.isDueAt(DAY.plusDays(1), READ_AT.plusSeconds(30))).isTrue();
    }

    @Test
    void findsAMeetingFromItsStartToItsEndBetweenReadings() {
        CalendarReading reading = CalendarReading.of(DAY, READ_AT, List.of(SPRINT_REVIEW));

        assertThat(reading.meetingInProgressAt(SPRINT_REVIEW.start().minusSeconds(1), List.of())).isEmpty();
        assertThat(reading.meetingInProgressAt(SPRINT_REVIEW.start(), List.of())).contains(SPRINT_REVIEW);
        assertThat(reading.meetingInProgressAt(SPRINT_REVIEW.end(), List.of())).isEmpty();
        assertThat(reading.meetingInProgressAt(SPRINT_REVIEW.start(), List.of("sprint"))).isEmpty();
    }

    @Test
    void keepsTheDaysKnownMeetingsAndTheFirstFailureWhileTheCalendarStaysUnreadable() {
        Instant firstFailure = READ_AT.plus(CalendarReading.READ_AGAIN_AFTER);
        CalendarReading failed = CalendarReading.of(DAY, READ_AT, List.of(SPRINT_REVIEW)).failedAt(DAY, firstFailure, " Outlook did not answer. ");
        CalendarReading failedAgain = failed.failedAt(DAY, firstFailure.plus(CalendarReading.READ_AGAIN_AFTER), "Outlook is closed.");

        assertThat(failed.isReadable()).isFalse();
        assertThat(failed.unreadable()).isEqualTo(new UnreadableCalendar(firstFailure, "Outlook did not answer."));
        assertThat(failedAgain.unreadable()).isEqualTo(new UnreadableCalendar(firstFailure, "Outlook is closed."));
        assertThat(failedAgain.entries()).containsExactly(SPRINT_REVIEW);
        assertThat(failedAgain.isDueAt(DAY, firstFailure.plus(CalendarReading.READ_AGAIN_AFTER).plusSeconds(119))).isFalse();
    }

    @Test
    void forgetsYesterdaysMeetingsWhenTodaysCalendarCannotBeRead() {
        CalendarReading failed = CalendarReading.of(DAY, READ_AT, List.of(SPRINT_REVIEW)).failedAt(DAY.plusDays(1), READ_AT.plus(Duration.ofDays(1)),
                "Outlook did not answer.");

        assertThat(failed.entries()).isEmpty();
        assertThat(failed.day()).isEqualTo(DAY.plusDays(1));
    }

    @Test
    void isReadableAgainOnceAReadingSucceeds() {
        CalendarReading failed = CalendarReading.NOT_YET_READ.failedAt(DAY, READ_AT, "Outlook did not answer.");

        assertThat(failed.unreadable().since()).isEqualTo(READ_AT);
        assertThat(CalendarReading.of(DAY, READ_AT.plus(CalendarReading.READ_AGAIN_AFTER), List.of()).isReadable()).isTrue();
    }
}
