package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;

public record Route(ModelId model, EffortLevel effort) {

    public Route {
        Objects.requireNonNull(model, "A worker is launched with an explicit model.");
    }

    public boolean namesAnEffort() {
        return effort != null;
    }

    public boolean isAboveTheEffortCeiling() {
        return namesAnEffort() && effort.isAboveTheCeiling();
    }

    @Override
    public String toString() {
        return namesAnEffort() ? model + " at " + effort.word() : model + " without an effort flag";
    }
}
