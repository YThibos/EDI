package io.github.ythibos.edi.domain.task;

import java.time.LocalDate;

public sealed interface HoldUntil {

    static HoldUntil day(LocalDate date) {
        return new Day(date);
    }

    static HoldUntil captainsWord() {
        return new CaptainsWord();
    }

    record Day(LocalDate date) implements HoldUntil {
    }

    record CaptainsWord() implements HoldUntil {
    }
}
