package io.github.ythibos.edi.web.access;

import io.github.ythibos.edi.domain.rule.Rule;

public class NotPermitted extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Rule rule;

    public NotPermitted(String reason) {
        this(Rule.CALLER_ROLE, reason);
    }

    public NotPermitted(Rule rule, String reason) {
        super(reason);
        this.rule = rule;
    }

    public Rule rule() {
        return rule;
    }
}
