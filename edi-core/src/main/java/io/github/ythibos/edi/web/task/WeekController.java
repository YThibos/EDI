package io.github.ythibos.edi.web.task;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.task.WeekOfWork;
import io.github.ythibos.edi.application.task.WorkedWeek;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.NotPermitted;

@RestController
class WeekController {

    private final WeekOfWork week;

    WeekController(WeekOfWork week) {
        this.week = week;
    }

    @GetMapping("/api/week")
    WorkedWeek week(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, Caller caller) {
        if (caller instanceof Caller.WorkerCaller) {
            throw new NotPermitted("The week's worked time is the primary's and the captain's to read; a worker reports through edi status.");
        }
        return week.from(from);
    }
}
