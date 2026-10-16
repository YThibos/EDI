package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;
import java.util.Set;

public record PipelineRun(String id, String status, String activeStep, String headCommit, String mergeRequestUrl) {

    private static final Set<String> FINISHED = Set.of("completed", "failed", "cancelled", "canceled", "aborted");

    public PipelineRun {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A pipeline run has the id the pipeline gave it.");
        }
        Objects.requireNonNull(status, () -> "Pipeline run " + id + " has a status.");
        activeStep = activeStep == null || activeStep.isBlank() ? null : activeStep;
        mergeRequestUrl = mergeRequestUrl == null || mergeRequestUrl.isBlank() ? null : mergeRequestUrl;
    }

    public boolean isRunning() {
        return !FINISHED.contains(status);
    }
}
