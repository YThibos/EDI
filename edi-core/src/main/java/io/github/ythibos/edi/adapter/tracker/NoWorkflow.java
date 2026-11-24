package io.github.ythibos.edi.adapter.tracker;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

final class NoWorkflow {

    private NoWorkflow() {
    }

    static Refusal refusedFor(TrackerKind kind) {
        return new Refusal(Rule.TRACKER_CONFIGURED, "A project on " + kind.word() + " keys has no tracker workflow to walk a ticket through or to compare with its repository.");
    }
}
