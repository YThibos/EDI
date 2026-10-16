package io.github.ythibos.edi.adapter.store;

import java.time.Instant;
import java.time.LocalDate;

import io.github.ythibos.edi.domain.task.HoldUntil;

final class Columns {

    private Columns() {
    }

    static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    static Instant instant(String text) {
        return text == null ? null : Instant.parse(text);
    }

    static String text(HoldUntil until) {
        return switch (until) {
            case HoldUntil.Day day -> day.date().toString();
            case HoldUntil.CaptainsWord _ -> null;
        };
    }

    static HoldUntil holdUntil(String text) {
        return text == null ? HoldUntil.captainsWord() : HoldUntil.day(LocalDate.parse(text));
    }

    static String textOf(Object value) {
        return value == null ? null : value.toString();
    }
}
