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
        Table table = new Table("  ");
        table.line("Rules that fired since " + moments.minute(report.since()) + ": " + report.fired().size() + ".");
        report.fired().forEach(rule -> table.row(rule.rule(), String.valueOf(rule.fired()), outcomesOf(rule),
                "last " + moments.minute(rule.lastFired())));
        table.line("Rules that never fired in that time: " + report.neverFired().size() + ".");
        report.neverFired().forEach(rule -> table.row(rule.rule(), rule.protects()));
        return table.text();
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
