package io.github.ythibos.edi.web.rule;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.rule.RuleReport;
import io.github.ythibos.edi.application.rule.RuleReportQuery;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.CallerFirings;

@RestController
class RulesController {

    private static final int LONGEST_PERIOD_IN_DAYS = 366;

    private final RuleReportQuery report;
    private final CallerFirings firings;
    private final Clock clock;

    RulesController(RuleReportQuery report, CallerFirings firings, Clock clock) {
        this.report = report;
        this.firings = firings;
        this.clock = clock;
    }

    @GetMapping("/api/rules")
    RuleReport rules(@RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since, Caller caller) {
        caller.requireThePrimaryOrTheCaptain("The rules' firings");
        return report.since(since == null ? clock.now().minus(Duration.ofDays(periodOf(days))) : since.atStartOfDay(clock.zone()).toInstant());
    }

    @PostMapping("/api/rules/fired")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void fired(@RequestBody RuleFiringRequest request, Caller caller) {
        caller.requireThePrimaryOrAWorker("A rule's firing");
        firings.fired(request.firedRule(), request.firedOutcome(), caller, request.reason());
    }

    private static int periodOf(int days) {
        if (days < 1 || days > LONGEST_PERIOD_IN_DAYS) {
            throw new IllegalArgumentException("A period runs from 1 to " + LONGEST_PERIOD_IN_DAYS + " days, not " + days + ".");
        }
        return days;
    }
}
