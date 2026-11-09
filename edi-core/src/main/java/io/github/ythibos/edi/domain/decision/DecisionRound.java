package io.github.ythibos.edi.domain.decision;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;

public record DecisionRound(Instant opened, List<DecisionNumber> decisions) {

    public static final Duration WINDOW = Duration.ofMinutes(5);

    public DecisionRound {
        decisions = List.copyOf(decisions);
    }

    public static boolean waitsForItsRound(Decision decision, Task task) {
        return !decision.card().urgent() && isARunningShip(task);
    }

    public static Optional<DecisionRound> gatheredFrom(List<Decision> waiting) {
        List<Decision> inTheOrderRaised = waiting.stream().sorted(Comparator.comparing(Decision::raisedAt)).toList();
        return inTheOrderRaised.stream()
                .findFirst()
                .map(first -> new DecisionRound(first.raisedAt(), inTheOrderRaised.stream().map(Decision::number).toList()));
    }

    public Instant closes() {
        return opened.plus(WINDOW);
    }

    public boolean isDue(Instant now) {
        return !now.isBefore(closes());
    }

    public boolean gathers(DecisionNumber number) {
        return decisions.contains(number);
    }

    private static boolean isARunningShip(Task task) {
        return task.intake().kind() == TaskKind.SHIP && task.state() == TaskState.IN_FLIGHT;
    }
}
