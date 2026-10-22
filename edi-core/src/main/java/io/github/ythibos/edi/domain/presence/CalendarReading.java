package io.github.ythibos.edi.domain.presence;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record CalendarReading(LocalDate day, Instant readAt, List<CalendarEntry> entries, UnreadableCalendar unreadable) {

    public static final Duration READ_AGAIN_AFTER = Duration.ofMinutes(2);
    public static final CalendarReading NOT_YET_READ = new CalendarReading(LocalDate.MIN, Instant.MIN, List.of(), null);

    public CalendarReading {
        entries = List.copyOf(entries);
    }

    public static CalendarReading of(LocalDate day, Instant readAt, List<CalendarEntry> entries) {
        return new CalendarReading(day, readAt, entries, null);
    }

    public boolean isDueAt(LocalDate today, Instant now) {
        return !day.equals(today) || !now.isBefore(readAt.plus(READ_AGAIN_AFTER));
    }

    public CalendarReading failedAt(LocalDate today, Instant now, String reason) {
        return new CalendarReading(today, now, entriesStillKnownOn(today), unreadableSince(now, reason));
    }

    public boolean isReadable() {
        return unreadable == null;
    }

    public Optional<CalendarEntry> meetingInProgressAt(Instant now, List<String> ignored) {
        return entries.stream().filter(entry -> entry.isAMeetingInProgressAt(now, ignored)).findFirst();
    }

    private List<CalendarEntry> entriesStillKnownOn(LocalDate today) {
        return day.equals(today) ? entries : List.of();
    }

    private UnreadableCalendar unreadableSince(Instant now, String reason) {
        return isReadable() ? new UnreadableCalendar(now, reason) : unreadable.stillUnreadable(reason);
    }
}
