package io.github.ythibos.edi.application.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.adapter.store.InboxStore;
import io.github.ythibos.edi.adapter.store.LoggedEvent;
import io.github.ythibos.edi.adapter.store.TaskStore;
import io.github.ythibos.edi.application.board.AnsweredRounds.Round;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardAnswered;
import io.github.ythibos.edi.domain.event.DomainEvent.SteerSent;
import io.github.ythibos.edi.domain.task.TaskId;

@Service
public class BoardRoundsQuery {

    private final TaskStore tasks;
    private final EventLog events;
    private final InboxStore inbox;
    private final BoardStore boards;
    private final TransactionTemplate transaction;

    public BoardRoundsQuery(TaskStore tasks, EventLog events, InboxStore inbox, BoardStore boards, TransactionTemplate transaction) {
        this.tasks = tasks;
        this.events = events;
        this.inbox = inbox;
        this.boards = boards;
        this.transaction = transaction;
    }

    public AnsweredRounds roundsOf(TaskReference reference, Integer round) {
        return transaction.execute(_ -> {
            TaskId task = tasks.get(reference).id();
            List<Round> rounds = roundsIn(task).stream().filter(shown -> round == null || shown.number() == round).toList();
            if (round != null && rounds.isEmpty()) {
                throw new Refusal(task + " has no board round " + round + "; edi board show " + task + " lists its rounds.");
            }
            return new AnsweredRounds(task, rounds);
        });
    }

    private List<Round> roundsIn(TaskId task) {
        List<LoggedEvent> timeline = events.ofTask(task);
        List<Round> rounds = new ArrayList<>();
        for (int position = 1; position < timeline.size(); position++) {
            roundAt(task, timeline.get(position - 1), timeline.get(position)).ifPresent(rounds::add);
        }
        return rounds;
    }

    private Optional<Round> roundAt(TaskId task, LoggedEvent sent, LoggedEvent answered) {
        if (!answered.isA(BoardAnswered.class) || !sent.isA(SteerSent.class)) {
            return Optional.empty();
        }
        BoardId board = new BoardId(answered.payload().path("board").asLong());
        int number = answered.payload().path("round").asInt();
        return inbox.get(task, sent.payload().path("number").asInt())
                .map(message -> new Round(board, boards.get(board).map(Board::file).orElse(""), number, answered.at(), message.body(),
                        boards.answersOf(board).stream().filter(answer -> answer.round() == number).toList()));
    }
}
