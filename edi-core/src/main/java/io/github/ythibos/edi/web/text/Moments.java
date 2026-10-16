package io.github.ythibos.edi.web.text;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import io.github.ythibos.edi.domain.task.HoldUntil;

public final class Moments {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private Moments() {
    }

    public static String minute(Instant instant) {
        return MINUTE.format(instant);
    }

    public static String day(Instant instant) {
        return DAY.format(instant);
    }

    public static String until(HoldUntil until) {
        return switch (until) {
            case HoldUntil.Day day -> "until " + day.date();
            case HoldUntil.CaptainsWord _ -> "until your word";
        };
    }
}
