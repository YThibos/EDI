package io.github.ythibos.edi.application.task;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record WorkedWeek(LocalDate from, LocalDate thisWeek, ZoneId zone, List<LocalDate> days, List<WorkedTask> tasks) {

    public WorkedWeek {
        days = List.copyOf(days);
        tasks = List.copyOf(tasks);
    }

    public record WorkedTask(TaskId task, String title, WorkKey key, WorkKey bookedOn, List<WorkedDay> days) {

        public WorkedTask {
            days = List.copyOf(days);
        }
    }

    public record WorkedDay(LocalDate day, long activeMinutes, long wallClockMinutes) {
    }
}
