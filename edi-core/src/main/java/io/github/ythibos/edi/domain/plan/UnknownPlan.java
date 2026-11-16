package io.github.ythibos.edi.domain.plan;

import io.github.ythibos.edi.domain.project.WorkKey;

public class UnknownPlan extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownPlan(WorkKey key) {
        super("No plan is known by " + key + "; edi plan add enters one.");
    }
}
