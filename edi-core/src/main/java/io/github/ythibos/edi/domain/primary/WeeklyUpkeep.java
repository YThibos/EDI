package io.github.ythibos.edi.domain.primary;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class WeeklyUpkeep {

    private static final DayOfWeek UPKEEP_DAY = DayOfWeek.MONDAY;

    private WeeklyUpkeep() {
    }

    public static boolean isSuggestedOn(LocalDate day) {
        return day.getDayOfWeek() == UPKEEP_DAY;
    }
}
