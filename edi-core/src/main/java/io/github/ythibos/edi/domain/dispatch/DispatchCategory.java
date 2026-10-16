package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;

import io.github.ythibos.edi.domain.branch.BranchKind;

public record DispatchCategory(CategoryId id, String when, Route route, String why, BranchKind branch, boolean researchFirst) {

    public DispatchCategory {
        Objects.requireNonNull(id, "A dispatch category has an id.");
        Objects.requireNonNull(route, () -> "The category " + id + " names the model it runs on.");
        when = Objects.requireNonNullElse(when, "").strip();
        why = Objects.requireNonNullElse(why, "").strip();
        branch = Objects.requireNonNullElse(branch, BranchKind.FEATURE);
    }

    public boolean statesWhy() {
        return !why.isEmpty();
    }
}
