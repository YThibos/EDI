package io.github.ythibos.edi.application.decision;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionRound;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRoundClosed;

@Service
public class DecisionRoundQuery {

    private final DecisionStore decisions;
    private final TaskStore tasks;
    private final EventLog events;
    private final TransactionTemplate transaction;

    public DecisionRoundQuery(DecisionStore decisions, TaskStore tasks, EventLog events, TransactionTemplate transaction) {
        this.decisions = decisions;
        this.tasks = tasks;
        this.events = events;
        this.transaction = transaction;
    }

    public Optional<DecisionRound> gathering() {
        return transaction.execute(_ -> DecisionRound.gatheredFrom(waitingForTheirRound()));
    }

    private List<Decision> waitingForTheirRound() {
        List<Decision> batched = decisions.open().stream()
                .filter(decision -> DecisionRound.waitsForItsRound(decision, tasks.get(decision.task())))
                .toList();
        List<LoggedEvent> closings = roundsClosedSince(batched);
        return batched.stream().filter(decision -> !wentOutInARound(decision, closings)).toList();
    }

    private List<LoggedEvent> roundsClosedSince(List<Decision> batched) {
        return batched.stream()
                .map(Decision::raisedAt)
                .min(Comparator.naturalOrder())
                .map(earliest -> events.ofTypesSince(Set.of(DecisionRoundClosed.class.getSimpleName()), earliest))
                .orElse(List.of());
    }

    private static boolean wentOutInARound(Decision decision, List<LoggedEvent> closings) {
        String number = decision.number().toString();
        Instant raised = decision.raisedAt();
        return closings.stream().anyMatch(closing -> number.equals(closing.text("number")) && !closing.at().isBefore(raised));
    }
}
