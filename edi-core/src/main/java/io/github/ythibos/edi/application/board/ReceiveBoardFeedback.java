package io.github.ythibos.edi.application.board;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.DecisionStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.application.worker.InboxNotice;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardClosed;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class ReceiveBoardFeedback {

    private final BoardStore boards;
    private final DecisionStore decisions;
    private final InboxStore inbox;
    private final WorkerStore workers;
    private final EventLog events;
    private final InboxNotice notice;
    private final SettleBoardAnswers settlement;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ReceiveBoardFeedback(BoardStore boards, DecisionStore decisions, InboxStore inbox, WorkerStore workers, EventLog events,
            InboxNotice notice, SettleBoardAnswers settlement, Clock clock, TransactionTemplate transaction) {
        this.boards = boards;
        this.decisions = decisions;
        this.inbox = inbox;
        this.workers = workers;
        this.events = events;
        this.notice = notice;
        this.settlement = settlement;
        this.clock = clock;
        this.transaction = transaction;
    }

    public boolean receive(BoardId board, BoardFeedback feedback) {
        return switch (feedback) {
            case BoardFeedback.Answered answered -> answeredRound(board, answered);
            case BoardFeedback.ClosedEmpty _ -> closedWithoutNews(board);
            case BoardFeedback.Disconnected _, BoardFeedback.Failed _ -> false;
        };
    }

    private boolean answeredRound(BoardId board, BoardFeedback.Answered answered) {
        Optional<RecordedRound> recorded = transaction.execute(_ -> listening(board).map(listening -> recorded(listening, answered)));
        recorded.filter(RecordedRound::reachesAWorker).ifPresent(round -> notice.deliver(round.worker(), round.message()));
        settlement.settleEveryUnsettled();
        return recorded.isPresent() && !answered.sessionEnded();
    }

    private Optional<Board> listening(BoardId board) {
        return boards.get(board).filter(Board::isListening);
    }

    private RecordedRound recorded(Board board, BoardFeedback.Answered answered) {
        Instant now = clock.now();
        Board counted = board.answeredOnceMore();
        Board saved = answered.sessionEnded() ? counted.ended(now) : counted;
        boards.update(saved);
        List<BoardAnswer> answers = saved.answersIn(answered, decision -> decisions.get(decision).card(), now);
        answers.forEach(boards::save);
        List<BoardAnswer> foreseen = answers.stream().map(answer -> foreseenSettlement(answer, now)).toList();
        InboxMessage message = InboxMessage.sent(saved.task(), inbox.nextNumber(saved.task()), BoardRoundMessage.of(saved, answered, foreseen), null, now);
        inbox.save(message);
        events.record(new SteerSent(saved.task(), message.number(), null), Actor.CORE, now);
        events.record(new BoardAnswered(saved.task(), saved.id(), saved.rounds(), answered.prompts().size(), answered.sessionEnded()), Actor.CAPTAIN,
                now);
        return new RecordedRound(message, workers.latestOf(saved.task()).filter(worker -> !worker.isRetired()).orElse(null));
    }

    private BoardAnswer foreseenSettlement(BoardAnswer answer, Instant now) {
        Decision answered = decisions.get(answer.decision());
        return answered.isOpen() || answer.repeats(answered.answer()) ? answer : answer.refused(answered.whyItTakesNoOtherAnswer(), now);
    }

    private boolean closedWithoutNews(BoardId board) {
        transaction.executeWithoutResult(_ -> listening(board).ifPresent(listening -> {
            Instant now = clock.now();
            boards.update(listening.ended(now));
            events.record(new BoardClosed(listening.task(), listening.id()), Actor.CAPTAIN, now);
        }));
        return false;
    }

    private record RecordedRound(InboxMessage message, Worker worker) {

        boolean reachesAWorker() {
            return worker != null;
        }
    }
}
