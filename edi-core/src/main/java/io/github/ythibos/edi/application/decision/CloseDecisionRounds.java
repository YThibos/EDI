package io.github.ythibos.edi.application.decision;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionRound;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.DecisionRoundClosed;

@Service
public class CloseDecisionRounds {

    private final DecisionRoundQuery rounds;
    private final DecisionStore decisions;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CloseDecisionRounds(DecisionRoundQuery rounds, DecisionStore decisions, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.rounds = rounds;
        this.decisions = decisions;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public void closeTheDueRound() {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            rounds.gathering()
                    .filter(round -> round.isDue(now))
                    .ifPresent(round -> close(round, now));
        });
    }

    private void close(DecisionRound round, Instant now) {
        round.decisions().stream()
                .map(decisions::get)
                .forEach(decision -> events.record(closedIn(round, decision), Actor.CORE, now));
    }

    private static DecisionRoundClosed closedIn(DecisionRound round, Decision decision) {
        return new DecisionRoundClosed(decision.task(), decision.number(), decision.card().title(), round.opened());
    }
}
