package io.github.ythibos.edi.application.presence;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.windows.OutlookCalendar;
import io.github.ythibos.edi.domain.presence.CalendarEntry;

@Component
public class MeetingCalendar {

    private static final Logger LOG = LoggerFactory.getLogger(MeetingCalendar.class);
    private static final Duration FRESH_FOR = Duration.ofMinutes(15);

    private final OutlookCalendar outlook;
    private final PersonalConfiguration personal;
    private Reading latest;

    public MeetingCalendar(OutlookCalendar outlook, PersonalConfiguration personal) {
        this.outlook = outlook;
        this.personal = personal;
    }

    public Optional<CalendarEntry> meetingInProgressAt(Instant now) {
        if (!outlook.isConfigured()) {
            return Optional.empty();
        }
        List<String> ignored = personal.presence().calendarIgnore();
        return entriesOf(now).stream().filter(entry -> entry.isAMeetingInProgressAt(now, ignored)).findFirst();
    }

    private synchronized List<CalendarEntry> entriesOf(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneId.systemDefault());
        if (latest == null || !latest.isFreshFor(today, now)) {
            latest = read(today, now);
        }
        return latest.entries();
    }

    private Reading read(LocalDate today, Instant now) {
        try {
            return new Reading(today, now, outlook.entriesOn(today));
        } catch (ProgramFailure unreadable) {
            LOG.warn("The calendar could not be read, so presence follows the captain's typing alone: {}", unreadable.getMessage());
            return new Reading(today, now, List.of());
        }
    }

    private record Reading(LocalDate day, Instant readAt, List<CalendarEntry> entries) {

        boolean isFreshFor(LocalDate today, Instant now) {
            return day.equals(today) && now.isBefore(readAt.plus(FRESH_FOR));
        }
    }
}
