package io.github.ythibos.edi.application.call;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.CallTickStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.DeliveryStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.call.CallKey;
import io.github.ythibos.edi.domain.call.UnknownCall;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.CallTicked;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class TickACall {

    private final DecisionStore decisions;
    private final BoardStore boards;
    private final DeliveryStore deliveries;
    private final CallTickStore ticks;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TickACall(DecisionStore decisions, BoardStore boards, DeliveryStore deliveries, CallTickStore ticks, EventLog events, Clock clock,
            TransactionTemplate transaction) {
        this.decisions = decisions;
        this.boards = boards;
        this.deliveries = deliveries;
        this.ticks = ticks;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public CallKey tick(CallKey call, Actor actor) {
        return transaction.execute(_ -> {
            DealtWithCall dealtWith = dealtWith(call);
            Instant now = clock.now();
            ticks.tick(call, dealtWith.request(), now);
            events.record(new CallTicked(dealtWith.task(), call), actor, now);
            return call;
        });
    }

    private DealtWithCall dealtWith(CallKey call) {
        return switch (call) {
            case CallKey.OfADecision decision -> answered(decisions.get(decision.number()), call);
            case CallKey.OfABoard board -> ended(boards.get(board.board()).orElseThrow(() -> new UnknownCall(call)), call);
            case CallKey.OfAReview review -> settled(deliveries.of(review.task()).orElseThrow(() -> new UnknownCall(call)), call);
        };
    }

    private static DealtWithCall answered(Decision decision, CallKey call) {
        if (decision.isOpen()) {
            throw stillWaiting(call, "it is not answered yet");
        }
        return new DealtWithCall(decision.task(), null);
    }

    private static DealtWithCall ended(Board board, CallKey call) {
        if (board.isListening()) {
            throw stillWaiting(call, "the board still listens");
        }
        return new DealtWithCall(board.task(), null);
    }

    private static DealtWithCall settled(Delivery delivery, CallKey call) {
        if (delivery.isOpen()) {
            throw stillWaiting(call, "its request is still open");
        }
        return new DealtWithCall(delivery.task(), delivery.address().url());
    }

    private static Refusal stillWaiting(CallKey call, String because) {
        return new Refusal(Rule.TICKED_WHEN_DEALT_WITH, call + " still waits on the captain, since " + because + "; it can be ticked once it is dealt with.");
    }

    private record DealtWithCall(TaskId task, String request) {
    }
}
