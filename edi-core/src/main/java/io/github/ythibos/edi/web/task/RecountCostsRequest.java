package io.github.ythibos.edi.web.task;

import static io.github.ythibos.edi.web.RequestValues.required;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

record RecountCostsRequest(String since) {

    LocalDate day() {
        String day = required(since, "since");
        try {
            return LocalDate.parse(day);
        } catch (DateTimeParseException notADate) {
            throw new IllegalArgumentException("A recount starts on a day such as 2026-11-02, not " + day + ".", notADate);
        }
    }
}
