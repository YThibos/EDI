package io.github.ythibos.edi.application.fleet;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.task.Task;

public record ChangingRequest(Task task, Delivery delivery, PipelineRun runningPipeline, List<Integer> unacknowledgedSteers) {

    public ChangingRequest {
        unacknowledgedSteers = List.copyOf(unacknowledgedSteers);
    }

    public String whyChanging() {
        return Stream.of(pipelineStillRunning(), steersStillWaiting()).flatMap(Optional::stream).collect(Collectors.joining("; "));
    }

    private Optional<String> pipelineStillRunning() {
        return Optional.ofNullable(runningPipeline).map(run -> "pipeline " + run.currentStep());
    }

    private Optional<String> steersStillWaiting() {
        if (unacknowledgedSteers.isEmpty()) {
            return Optional.empty();
        }
        String numbers = unacknowledgedSteers.stream().map(String::valueOf).collect(Collectors.joining(", "));
        return Optional.of((unacknowledgedSteers.size() == 1 ? "steer " : "steers ") + numbers + " not acknowledged");
    }
}
