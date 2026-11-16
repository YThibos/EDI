package io.github.ythibos.edi.domain.primary;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class WeeklyGardening {

    private static final DayOfWeek GARDENING_DAY = DayOfWeek.MONDAY;
    private static final LocalTime WEEKLY_USAGE_RESET = LocalTime.of(9, 0);

    private WeeklyGardening() {
    }

    public static boolean isDueAt(LocalDateTime moment) {
        return GARDENING_DAY.equals(moment.getDayOfWeek()) && !moment.toLocalTime().isBefore(WEEKLY_USAGE_RESET);
    }
}
