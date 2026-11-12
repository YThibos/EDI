package io.github.ythibos.edi.web.access;

import io.github.ythibos.edi.domain.rule.Rule;

public class NotPermitted extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotPermitted(String reason) {
        super(reason);
    }

    public Rule rule() {
        return Rule.CALLER_ROLE;
    }
}
