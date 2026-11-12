package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;
import java.util.Set;

public record PipelineRun(String id, String status, String activeStep, String headCommit, String mergeRequestUrl, boolean checksPassed) {

    private static final Set<String> FINISHED =
            Set.of("completed", "failed", "cancelled", "canceled", "aborted", "checks_passed", "handed_off", "ci_monitor_interrupted");
    private static final String COMPLETED = "completed";
    private static final String CI_WATCH = "ci";

    public PipelineRun {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A pipeline run has the id the pipeline gave it.");
        }
        Objects.requireNonNull(status, () -> "Pipeline run " + id + " has a status.");
        activeStep = activeStep == null || activeStep.isBlank() ? null : activeStep;
        mergeRequestUrl = mergeRequestUrl == null || mergeRequestUrl.isBlank() ? null : mergeRequestUrl;
    }

    public PipelineRun withChecksPassed(boolean passed) {
        return new PipelineRun(id, status, activeStep, headCommit, mergeRequestUrl, passed);
    }

    public boolean isRunning() {
        return !FINISHED.contains(status);
    }

    public boolean stillChangesTheRequest() {
        return isRunning() ? !onlyWatchesPassedChecks() : endedWithoutPassedChecks();
    }

    public String currentStep() {
        return activeStep == null ? status : activeStep;
    }

    private boolean onlyWatchesPassedChecks() {
        return CI_WATCH.equals(activeStep) && checksPassed;
    }

    private boolean endedWithoutPassedChecks() {
        return !COMPLETED.equals(status) && !checksPassed;
    }
}
