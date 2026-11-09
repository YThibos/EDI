package io.github.ythibos.edi.web.access;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.rule.FiredRules;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.rule.RuleOutcome;
import io.github.ythibos.edi.domain.rule.RuleReason;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CallerFirings {

    private static final Actor UNKNOWN_CALLER = new Actor("unknown caller");

    private final FiredRules firedRules;

    public CallerFirings(FiredRules firedRules) {
        this.firedRules = firedRules;
    }

    public void refused(Rule rule, HttpServletRequest request, String reason) {
        Object caller = request.getAttribute(Caller.ATTRIBUTE);
        if (caller instanceof Caller known) {
            fired(rule, RuleOutcome.REFUSED, known, reason);
        } else {
            firedRules.record(new RuleFired(rule, RuleOutcome.REFUSED, UNKNOWN_CALLER, null, null, RuleReason.of(reason)));
        }
    }

    public void fired(Rule rule, RuleOutcome outcome, Caller caller, String reason) {
        firedRules.record(new RuleFired(rule, outcome, caller.actor(), caller.task().orElse(null), caller.claudeSession().orElse(null),
                RuleReason.of(reason)));
    }
}
