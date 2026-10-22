package io.github.ythibos.edi.application.presence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.windows.OutlookCalendar;
import io.github.ythibos.edi.domain.presence.CalendarEntry;
import io.github.ythibos.edi.domain.presence.CalendarReading;
import io.github.ythibos.edi.domain.presence.UnreadableCalendar;

@Component
public class MeetingCalendar {

    private static final Logger LOG = LoggerFactory.getLogger(MeetingCalendar.class);

    private final OutlookCalendar outlook;
    private final PersonalConfiguration personal;
    private volatile CalendarReading latest = CalendarReading.NOT_YET_READ;

    public MeetingCalendar(OutlookCalendar outlook, PersonalConfiguration personal) {
        this.outlook = outlook;
        this.personal = personal;
    }

    public Optional<CalendarEntry> meetingInProgressAt(Instant now) {
        if (!outlook.isConfigured()) {
            return Optional.empty();
        }
        return readingAt(now).meetingInProgressAt(now, personal.presence().calendarIgnore());
    }

    public Optional<UnreadableCalendar> unreadable() {
        return outlook.isConfigured() ? Optional.ofNullable(latest.unreadable()) : Optional.empty();
    }

    private synchronized CalendarReading readingAt(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneId.systemDefault());
        if (latest.isDueAt(today, now)) {
            latest = readOn(today, now);
        }
        return latest;
    }

    private CalendarReading readOn(LocalDate today, Instant now) {
        try {
            return readable(CalendarReading.of(today, now, outlook.entriesOn(today)));
        } catch (ProgramFailure unreadable) {
            return unreadable(latest.failedAt(today, now, unreadable.getMessage()));
        }
    }

    private CalendarReading readable(CalendarReading reading) {
        if (!latest.isReadable()) {
            LOG.info("The calendar can be read again, so a meeting turns presence quiet once more.");
        }
        return reading;
    }

    private CalendarReading unreadable(CalendarReading reading) {
        if (latest.isReadable()) {
            LOG.warn("The calendar cannot be read, so presence follows the captain's typing alone until it can: {}", reading.unreadable().reason());
        }
        return reading;
    }
}
