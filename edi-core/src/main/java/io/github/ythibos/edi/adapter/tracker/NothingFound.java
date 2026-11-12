package io.github.ythibos.edi.adapter.tracker;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

public class NothingFound extends Refusal {

    private static final long serialVersionUID = 1L;

    public NothingFound(String message) {
        super(Rule.TRACKER_ANSWERED, message);
    }
}
