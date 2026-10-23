package io.github.ythibos.edi.application.board;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.decision.AnswerDecision;
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswerRefused;

@Service
public class SettleBoardAnswers {

    private final BoardStore boards;
    private final DecisionStore decisions;
    private final EventLog events;
    private final AnswerDecision answerDecision;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SettleBoardAnswers(BoardStore boards, DecisionStore decisions, EventLog events, AnswerDecision answerDecision, Clock clock,
            TransactionTemplate transaction) {
        this.boards = boards;
        this.decisions = decisions;
        this.events = events;
        this.answerDecision = answerDecision;
        this.clock = clock;
        this.transaction = transaction;
    }

    public synchronized void settleEveryUnsettled() {
        transaction.execute(_ -> boards.unsettledAnswers()).forEach(this::settled);
    }

    private void settled(BoardAnswer answer) {
        try {
            if (!wasAlreadyRecorded(answer)) {
                answerDecision.answer(new TaskReference.ByDecision(answer.decision()),
                        new GivenAnswer(answer.verbatim(), answer.option(), "", AnswerChannel.BOARD), Actor.CAPTAIN);
            }
            transaction.executeWithoutResult(_ -> boards.save(answer.settled(clock.now())));
        } catch (Refusal | UnknownDecision | IllegalArgumentException refused) {
            refusedBecause(answer, refused.getMessage());
        }
    }

    private boolean wasAlreadyRecorded(BoardAnswer answer) {
        return answer.repeats(transaction.execute(_ -> decisions.get(answer.decision()).answer()));
    }

    private void refusedBecause(BoardAnswer answer, String because) {
        transaction.executeWithoutResult(_ -> {
            Instant now = clock.now();
            boards.save(answer.refused(because, now));
            boards.get(answer.board()).ifPresent(board -> events.record(new BoardAnswerRefused(board.task(), answer.decision(), board.id(), because),
                    Actor.CORE, now));
        });
    }
}
