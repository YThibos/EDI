package io.github.ythibos.edi.application.board;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.BoardStore;
import io.github.ythibos.edi.application.port.ResearchBoardHost;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardId;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class BoardListeners implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BoardListeners.class);

    private final BoardStore boards;
    private final ResearchBoardHost host;
    private final ReceiveBoardFeedback receive;
    private final SettleBoardAnswers settlement;
    private final TransactionTemplate transaction;
    private final Map<BoardId, Future<?>> listening = new ConcurrentHashMap<>();
    private final Map<BoardId, String> lastFailure = new ConcurrentHashMap<>();
    private final ExecutorService listeners = Executors.newVirtualThreadPerTaskExecutor();

    public BoardListeners(BoardStore boards, ResearchBoardHost host, ReceiveBoardFeedback receive, SettleBoardAnswers settlement,
            TransactionTemplate transaction) {
        this.boards = boards;
        this.host = host;
        this.receive = receive;
        this.settlement = settlement;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${edi.boards.round:PT30S}", initialDelayString = "${edi.boards.start:PT5S}")
    public void keepListening() {
        try {
            List<Board> armed = transaction.execute(_ -> boards.listening());
            stopListeningToAllBut(armed.stream().map(Board::id).collect(Collectors.toSet()));
            armed.forEach(this::listenTo);
            settlement.settleEveryUnsettled();
        } catch (RuntimeException failed) {
            LOG.warn("The board round stopped and runs again in its next round: {}", failed.getMessage());
        }
    }

    public void listenTo(Board board) {
        listening.compute(board.id(), (_, running) -> running == null || running.isDone() ? listeners.submit(() -> listen(board)) : running);
    }

    public void forget(TaskId task) {
        listening.keySet().stream()
                .filter(board -> transaction.execute(_ -> boards.get(board)).map(stored -> stored.task().equals(task)).orElse(true))
                .forEach(this::stop);
    }

    @Override
    public void close() {
        listeners.shutdownNow();
    }

    private void stopListeningToAllBut(Set<BoardId> armed) {
        listening.keySet().stream().filter(board -> !armed.contains(board)).forEach(this::stop);
    }

    private void stop(BoardId board) {
        Future<?> running = listening.remove(board);
        if (running != null) {
            running.cancel(true);
        }
    }

    private void listen(Board board) {
        try {
            boolean pollAgain = true;
            while (pollAgain && !Thread.currentThread().isInterrupted()) {
                BoardFeedback feedback = host.awaitFeedback(Path.of(board.file()), board.listener());
                noteFailure(board, feedback);
                pollAgain = receive.receive(board.id(), feedback);
            }
        } catch (RuntimeException failed) {
            if (!Thread.currentThread().isInterrupted()) {
                LOG.warn("The listener of board {} for {} stopped and starts again in the next round: {}", board.id(), board.task(), failed.getMessage());
            }
        }
    }

    private void noteFailure(Board board, BoardFeedback feedback) {
        if (!(feedback instanceof BoardFeedback.Failed failed)) {
            lastFailure.remove(board.id());
            return;
        }
        String previous = lastFailure.put(board.id(), failed.reason());
        if (!failed.reason().equals(previous)) {
            LOG.warn("Lavish did not answer the listener of board {} for {}; it asks again in the next round: {}", board.id(), board.task(),
                    failed.reason());
        }
    }
}
