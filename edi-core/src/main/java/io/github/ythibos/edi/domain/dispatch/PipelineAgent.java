package io.github.ythibos.edi.domain.dispatch;

import java.util.Objects;

public record PipelineAgent(Route route, String why) {

    public PipelineAgent {
        Objects.requireNonNull(route, "The pipeline entry names the model the validation pipeline's agent runs on.");
        why = Objects.requireNonNullElse(why, "").strip();
    }

    public boolean statesWhy() {
        return !why.isEmpty();
    }
}
