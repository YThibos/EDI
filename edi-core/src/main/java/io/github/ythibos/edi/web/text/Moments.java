package io.github.ythibos.edi.web.text;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.task.HoldUntil;

@Component
public class Moments {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm zzz", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final Clock clock;

    public Moments(Clock clock) {
        this.clock = clock;
    }

    public String minute(Instant instant) {
        return MINUTE.format(onTheMachine(instant));
    }

    public String clockTime(Instant instant) {
        return CLOCK_TIME.format(onTheMachine(instant));
    }

    public String day(Instant instant) {
        return onTheMachine(instant).toLocalDate().toString();
    }

    public static String until(HoldUntil until) {
        return switch (until) {
            case HoldUntil.Day day -> "until " + day.date();
            case HoldUntil.CaptainsWord _ -> "until your word";
        };
    }

    private ZonedDateTime onTheMachine(Instant instant) {
        return instant.atZone(clock.zone());
    }
}
