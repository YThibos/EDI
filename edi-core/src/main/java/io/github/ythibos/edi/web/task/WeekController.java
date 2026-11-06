package io.github.ythibos.edi.web.task;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.task.WeekOfWork;
import io.github.ythibos.edi.application.task.WorkedWeek;
import io.github.ythibos.edi.web.access.Caller;

@RestController
class WeekController {

    private final WeekOfWork week;

    WeekController(WeekOfWork week) {
        this.week = week;
    }

    @GetMapping("/api/week")
    WorkedWeek week(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, Caller caller) {
        caller.requireThePrimaryOrTheCaptain("The week's worked time");
        return from == null ? week.thisWeek() : week.from(from);
    }
}
