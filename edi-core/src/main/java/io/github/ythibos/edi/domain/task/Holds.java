package io.github.ythibos.edi.domain.task;

import java.util.List;
import java.util.Optional;
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
        return without(new OpenDecision(number));
    }

    public Holds released(Hold hold) {
        if (hold instanceof OpenDecision decision) {
            throw new Refusal(Rule.TASK_HOLD, decision.number() + " holds the task until it is answered; answering it releases it.");
        }
        return without(hold);
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    public List<DecisionNumber> openDecisions() {
        return held.stream().filter(OpenDecision.class::isInstance).map(OpenDecision.class::cast).map(OpenDecision::number).toList();
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

    private Holds without(Hold hold) {
        if (!held.contains(hold)) {
            throw new Refusal(Rule.TASK_HOLD, "The task does not wait: " + hold.reason() + ".");
        }
        return new Holds(held.stream().filter(other -> !other.equals(hold)).toList());
    }
}
