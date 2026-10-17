package io.github.ythibos.edi.domain.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record CalendarEntry(String subject, Instant start, Instant end, boolean allDay, Response response, Showing showing) {

    static final Duration LONGEST_MEETING = Duration.ofHours(4);
    private static final Set<Response> HIS_OWN = Set.of(Response.ORGANISED, Response.ACCEPTED, Response.OWN_APPOINTMENT);

    public CalendarEntry {
        subject = Objects.requireNonNullElse(subject, "");
        Objects.requireNonNull(start, "A calendar entry starts at some time.");
        Objects.requireNonNull(end, "A calendar entry ends at some time.");
        Objects.requireNonNull(response, "A calendar entry records the captain's response.");
        Objects.requireNonNull(showing, "A calendar entry shows him free, busy or otherwise.");
    }

    public boolean isAMeetingInProgressAt(Instant now, List<String> ignored) {
        return !now.isBefore(start) && now.isBefore(end) && isAMeeting(ignored);
    }

    public boolean isAMeeting(List<String> ignored) {
        return !allDay
                && end.isAfter(start)
                && Duration.between(start, end).compareTo(LONGEST_MEETING) < 0
                && showing == Showing.BUSY
                && HIS_OWN.contains(response)
                && ignored.stream().noneMatch(this::isMarkedBy);
    }

    private boolean isMarkedBy(String ignoredWords) {
        return !ignoredWords.isBlank() && subject.toLowerCase(Locale.ROOT).contains(ignoredWords.strip().toLowerCase(Locale.ROOT));
    }

    public enum Response {
        OWN_APPOINTMENT,
        ORGANISED,
        TENTATIVE,
        ACCEPTED,
        DECLINED,
        NOT_RESPONDED
    }

    public enum Showing {
        FREE,
        TENTATIVE,
        BUSY,
        OUT_OF_OFFICE,
        WORKING_ELSEWHERE
    }
}
