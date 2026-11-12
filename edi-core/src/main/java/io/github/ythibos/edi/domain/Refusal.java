package io.github.ythibos.edi.domain;

import java.util.Objects;

import io.github.ythibos.edi.domain.rule.Rule;


public class Refusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Rule rule;

    public Refusal(Rule rule, String reason) {
        super(reason);
        this.rule = Objects.requireNonNull(rule, "A refusal names the rule it applies.");
    }

    public Rule rule() {
        return rule;
    }
}
