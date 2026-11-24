package io.github.ythibos.edi.domain.task;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.Hold.OpenDecision;

public record Holds(List<Hold> held) {

    public Holds {
        held = List.copyOf(held);
    }

    public static Holds none() {
        return new Holds(List.of());
    }

    public Holds with(Hold hold) {
        refuseARepeatOf(hold);
        return new Holds(Stream.concat(held.stream(), Stream.of(hold)).toList());
    }

    public Holds answered(DecisionNumber number) {
        if (!openDecisions().contains(number)) {
            throw new Refusal(Rule.TASK_HOLD, "The task does not wait: " + number + " waits for its answer.");
        }
        return without(hold -> hold instanceof OpenDecision decision && decision.number().equals(number));
    }

    public Holds released(Hold hold) {
        if (hold instanceof OpenDecision decision) {
            throw new Refusal(Rule.TASK_HOLD, decision.number() + " holds the task until it is answered; answering it releases it.");
        }
        if (held.stream().noneMatch(hold::repeats)) {
            throw new Refusal(Rule.TASK_HOLD, "The task does not wait: " + hold.reason() + ".");
        }
        return without(hold::repeats);
    }

    public Holds released() {
        return without(not(Hold::isReleasedByItsAnswer));
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    public boolean holdsOnlyOpenDecisions() {
        return held.stream().allMatch(Hold::isReleasedByItsAnswer);
    }

    public List<DecisionNumber> openDecisions() {
        return held.stream().filter(OpenDecision.class::isInstance).map(OpenDecision.class::cast).map(OpenDecision::number).toList();
    }

    public List<Hold> releasable() {
        return held.stream().filter(not(Hold::isReleasedByItsAnswer)).toList();
    }

    public Optional<Hold> ofKind(HoldKind kind) {
        return held.stream().filter(hold -> hold.kind() == kind).findFirst();
    }

    public List<String> reasons() {
        return held.stream().map(Hold::reason).toList();
    }

    private void refuseARepeatOf(Hold hold) {
        Optional<Hold> repeated = held.stream().filter(hold::repeats).findFirst();
        if (repeated.isPresent()) {
            throw new Refusal(Rule.TASK_HOLD, "The task already waits: " + repeated.get().reason() + ".");
        }
    }

    private Holds without(Predicate<Hold> leaving) {
        return new Holds(held.stream().filter(not(leaving)).toList());
    }
}
