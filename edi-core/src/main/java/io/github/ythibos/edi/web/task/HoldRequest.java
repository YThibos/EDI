package io.github.ythibos.edi.web.task;

import static io.github.ythibos.edi.web.RequestValues.optional;
import static io.github.ythibos.edi.web.RequestValues.required;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;

record HoldRequest(String kind, String reason, String until) {

    HoldKind holdKind() {
        return HoldKind.named(required(kind, "kind"));
    }

    String why() {
        return required(reason, "reason");
    }

    HoldUntil holdUntil() {
        LocalDate day = optional(until, HoldRequest::date);
        return day == null ? HoldUntil.captainsWord() : HoldUntil.day(day);
    }

    private static LocalDate date(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException notADate) {
            throw new IllegalArgumentException("A hold lasts until a date such as 2026-11-01, not " + text + ".", notADate);
        }
    }
}
