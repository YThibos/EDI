package io.github.ythibos.edi.application.fleet;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.task.Task;

public record ChangingRequest(Task task, Delivery delivery, boolean workerBackAtWork, PipelineRun changingPipeline, List<Integer> unacknowledgedSteers) {

    public ChangingRequest {
        unacknowledgedSteers = List.copyOf(unacknowledgedSteers);
    }

    public String whyChanging() {
        return Stream.of(workerStillAtWork(), stillADraft(), pipelineStillChanging(), steersStillWaiting()).flatMap(Optional::stream).collect(Collectors.joining("; "));
    }

    private Optional<String> workerStillAtWork() {
        return workerBackAtWork ? Optional.of("worker back at work") : Optional.empty();
    }

    private Optional<String> stillADraft() {
        return delivery.draft() ? Optional.of("still a draft") : Optional.empty();
    }

    private Optional<String> pipelineStillChanging() {
        return Optional.ofNullable(changingPipeline).map(ChangingRequest::whyThePipelineStillChangesIt);
    }

    private static String whyThePipelineStillChangesIt(PipelineRun run) {
        return run.isRunning() ? "pipeline " + run.currentStep() : "pipeline " + run.status() + " without passed checks";
    }

    private Optional<String> steersStillWaiting() {
        if (unacknowledgedSteers.isEmpty()) {
            return Optional.empty();
        }
        String numbers = unacknowledgedSteers.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return Optional.of((unacknowledgedSteers.size() == 1 ? "steer " : "steers ") + numbers + " not acknowledged");
    }
}
