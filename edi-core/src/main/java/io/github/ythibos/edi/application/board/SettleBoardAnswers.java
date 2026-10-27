package io.github.ythibos.edi.application.board;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.decision.AnswerDecision;
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.InboxNotice;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswerRefused;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class SettleBoardAnswers {

    private final BoardStore boards;
    private final DecisionStore decisions;
    private final EventLog events;
    private final InboxStore inbox;
    private final WorkerStore workers;
    private final InboxNotice notice;
    private final AnswerDecision answerDecision;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SettleBoardAnswers(BoardStore boards, DecisionStore decisions, EventLog events, InboxStore inbox, WorkerStore workers, InboxNotice notice,
            AnswerDecision answerDecision, Clock clock, TransactionTemplate transaction) {
        this.boards = boards;
        this.decisions = decisions;
        this.events = events;
        this.inbox = inbox;
        this.workers = workers;
        this.notice = notice;
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
        Optional<RefusalNotice> told = transaction.execute(_ -> {
            Instant now = clock.now();
            boards.save(answer.refused(because, now));
            return boards.get(answer.board()).map(board -> refusalRecorded(board, answer, because, now));
        });
        told.filter(RefusalNotice::reachesAWorker).ifPresent(refusal -> notice.deliver(refusal.worker(), refusal.message()));
    }

    private RefusalNotice refusalRecorded(Board board, BoardAnswer answer, String because, Instant now) {
        events.record(new BoardAnswerRefused(board.task(), answer.decision(), board.id(), because), Actor.CORE, now);
        InboxMessage message = InboxMessage.sent(board.task(), inbox.nextNumber(board.task()), refusalWords(answer, because), null, now);
        inbox.save(message);
        events.record(new SteerSent(board.task(), message.number(), null), Actor.CORE, now);
        return new RefusalNotice(message, workers.latestOf(board.task()).filter(worker -> !worker.isRetired()).orElse(null));
    }

    private static String refusalWords(BoardAnswer answer, String because) {
        return "The captain's board answer to " + answer.decision() + " (\"" + answer.verbatim() + "\") was refused: " + because;
    }

    private record RefusalNotice(InboxMessage message, Worker worker) {

        boolean reachesAWorker() {
            return worker != null;
        }
    }
}
