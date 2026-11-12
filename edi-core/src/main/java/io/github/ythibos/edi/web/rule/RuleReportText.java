package io.github.ythibos.edi.web.rule;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.rule.RuleReport;
import io.github.ythibos.edi.application.rule.RuleReport.FiredRule;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class RuleReportText implements TextPresenter<RuleReport> {

    private final Moments moments;

    RuleReportText(Moments moments) {
        this.moments = moments;
    }

    @Override
    public Class<RuleReport> presents() {
        return RuleReport.class;
    }

    @Override
    public String text(RuleReport report) {
        return firedRules(report) + quietRules(report);
    }

    private String firedRules(RuleReport report) {
        Table fired = new Table("  ").line("Rules that fired since " + moments.minute(report.since()) + ": " + report.fired().size() + ".");
        report.fired().forEach(rule -> fired.row(rule.rule(), String.valueOf(rule.fired()), outcomesOf(rule),
                "last " + moments.minute(rule.lastFired())));
        return fired.text();
    }

    private static String quietRules(RuleReport report) {
        Table quiet = new Table("  ").line("Rules that never fired in that time: " + report.neverFired().size() + ".");
        report.neverFired().forEach(rule -> quiet.row(rule.rule(), rule.protects()));
        return quiet.text();
    }

    private static String outcomesOf(FiredRule rule) {
        List<String> outcomes = new ArrayList<>();
        counted(outcomes, rule.refused(), "refused");
        counted(outcomes, rule.warned(), "warned");
        counted(outcomes, rule.allowed(), "allowed after a check");
        return String.join(", ", outcomes);
    }

    private static void counted(List<String> outcomes, long count, String outcome) {
        if (count > 0) {
            outcomes.add(count + " " + outcome);
        }
    }
}
