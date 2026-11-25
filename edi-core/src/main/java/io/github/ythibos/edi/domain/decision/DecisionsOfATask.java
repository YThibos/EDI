package io.github.ythibos.edi.domain.decision;

import static java.util.function.Predicate.not;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.ythibos.edi.domain.worker.WorkerQuestion;

public record DecisionsOfATask(List<Decision> decisions, Map<DecisionNumber, Instant> firstRaises) {

    public DecisionsOfATask {
        decisions = List.copyOf(decisions);
        firstRaises = Map.copyOf(firstRaises);
    }

    public boolean answers(Decision answered, WorkerQuestion question) {
        return answering(question).filter(first -> first.number().equals(answered.number())).isPresent();
    }

    public Optional<Decision> answering(WorkerQuestion question) {
        return theOneRaisedFor(question).filter(not(Decision::isOpen));
    }

    public boolean isTheOneRaisedFor(Decision decision, WorkerQuestion question) {
        return theOneRaisedFor(question).filter(raised -> raised.number().equals(decision.number())).isPresent();
    }

    public boolean escalates(WorkerQuestion question) {
        return theOneRaisedFor(question).filter(Decision::isOpen).isPresent();
    }

    private Optional<Decision> theOneRaisedFor(WorkerQuestion question) {
        List<Decision> raisedFor = decisions.stream().filter(decision -> isRaisedFor(decision, question)).toList();
        return raisedFor.size() == 1 ? Optional.of(raisedFor.getFirst()) : Optional.empty();
    }

    private boolean isRaisedFor(Decision decision, WorkerQuestion question) {
        return decision.task().equals(question.task()) && !question.askedAt().isAfter(firstRaiseOf(decision));
    }

    private Instant firstRaiseOf(Decision decision) {
        return firstRaises.getOrDefault(decision.number(), decision.raisedAt());
    }
}
