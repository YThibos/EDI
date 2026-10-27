package io.github.ythibos.edi.testing;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.ythibos.edi.application.port.ResearchBoardHost;
import io.github.ythibos.edi.domain.board.BoardFeedback;

public final class FakeBoardHost implements ResearchBoardHost {

    private final Deque<BoardFeedback> rounds = new ArrayDeque<>();
    private final List<String> listeners = new ArrayList<>();
    private boolean holding;
    private CountDownLatch polled = new CountDownLatch(1);
    private final CountDownLatch neverOpens = new CountDownLatch(1);
    private final CountDownLatch stopped = new CountDownLatch(1);

    @Override
    public BoardFeedback awaitFeedback(Path board, String listener) {
        BoardFeedback next = nextFor(listener);
        if (next != null) {
            return next;
        }
        try {
            neverOpens.await();
            return new BoardFeedback.Disconnected();
        } catch (InterruptedException interrupted) {
            stopped.countDown();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The listener was stopped.", interrupted);
        }
    }

    public synchronized void answers(BoardFeedback round) {
        rounds.add(round);
    }

    public synchronized void holdsTheNextPollOpen() {
        holding = true;
    }

    public synchronized CountDownLatch expectPolls(int count) {
        polled = new CountDownLatch(count);
        return polled;
    }

    public synchronized List<String> listeners() {
        return List.copyOf(listeners);
    }

    public void awaitStopOfTheHeldPoll() throws InterruptedException {
        if (!stopped.await(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
            throw new AssertionError("The held poll was not stopped within " + Patience.FOR_REAL_WORK.toSeconds() + " seconds.");
        }
    }

    public static void await(CountDownLatch polls) throws InterruptedException {
        if (!polls.await(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
            throw new AssertionError("The board host was not polled as expected within " + Patience.FOR_REAL_WORK.toSeconds() + " seconds.");
        }
    }

    private synchronized BoardFeedback nextFor(String listener) {
        listeners.add(listener);
        polled.countDown();
        if (!rounds.isEmpty()) {
            return rounds.poll();
        }
        return holding ? null : new BoardFeedback.Disconnected();
    }
}
