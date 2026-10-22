package io.github.ythibos.edi.application.board;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.adapter.store.EventLog;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardRound;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.BoardRoundPosted;

@Service
public class PostBoardRound {

    private final BoardStore boards;
    private final EventLog events;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PostBoardRound(BoardStore boards, EventLog events, Clock clock, TransactionTemplate transaction) {
        this.boards = boards;
        this.events = events;
        this.clock = clock;
        this.transaction = transaction;
    }

    public Board post(Path file, String link, int questions, Actor actor) {
        return transaction.execute(_ -> {
            Instant now = clock.now();
            Board listening = listeningAt(file);
            Board posted = listening.posted(new BoardRound(link, questions, listening.bindings()));
            boards.update(posted);
            events.record(new BoardRoundPosted(posted.task(), posted.id(), posted.link(), posted.openQuestions()), actor, now);
            return posted;
        });
    }

    private Board listeningAt(Path file) {
        return boards.listeningAt(realPathOf(file).toString())
                .orElseThrow(() -> new Refusal("No research board listens at " + file + "; its scout arms it with edi board arm."));
    }

    private static Path realPathOf(Path file) {
        try {
            return file.toRealPath();
        } catch (IOException unreadable) {
            throw new Refusal("No research board listens at " + file + ": " + unreadable.getMessage());
        }
    }
}
