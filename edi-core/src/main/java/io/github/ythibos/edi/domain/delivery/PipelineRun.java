package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;
import java.util.Set;

public record PipelineRun(String id, String status, String step, boolean stoppedByThePipelineService, String headCommit, String mergeRequestUrl,
        boolean checksPassed) {

    private static final Set<String> FINISHED =
            Set.of("completed", "failed", "cancelled", "canceled", "aborted", "checks_passed", "handed_off", "ci_monitor_interrupted");
    private static final String COMPLETED = "completed";
    private static final String CI_WATCH = "ci";

    public PipelineRun {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A pipeline run has the id the pipeline gave it.");
        }
        Objects.requireNonNull(status, () -> "Pipeline run " + id + " has a status.");
        step = step == null || step.isBlank() ? null : step;
        mergeRequestUrl = mergeRequestUrl == null || mergeRequestUrl.isBlank() ? null : mergeRequestUrl;
    }

    public PipelineRun withChecksPassed(boolean passed) {
        return new PipelineRun(id, status, step, stoppedByThePipelineService, headCommit, mergeRequestUrl, passed);
    }

    public boolean isRunning() {
        return !FINISHED.contains(status);
    }

    public boolean stillChanges(Delivery request) {
        return isRunning() ? !onlyWatchesPassedChecks() : failedInAStepOfItsOwn() && !request.checksPassed();
    }

    public String currentStep() {
        return step == null ? status : step;
    }

    private boolean onlyWatchesPassedChecks() {
        return CI_WATCH.equals(step) && checksPassed;
    }

    private boolean failedInAStepOfItsOwn() {
        return !COMPLETED.equals(status) && step != null && !CI_WATCH.equals(step) && !stoppedByThePipelineService;
    }
}
