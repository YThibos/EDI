package io.github.ythibos.edi.web.rule;

import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleOutcome;

record RuleFiringRequest(String rule, String outcome, String reason) {

    Rule firedRule() {
        return Rule.withId(rule).orElseThrow(() -> new IllegalArgumentException("No rule of the catalogue has the id " + rule + "."));
    }

    RuleOutcome firedOutcome() {
        return RuleOutcome.named(outcome).orElseThrow(() -> new IllegalArgumentException(
                "A rule fires as refused, warned or allowed, not " + outcome + "."));
    }
}
