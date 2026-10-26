package io.github.ythibos.edi.application.fleet;

import java.util.List;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.task.Task;

public record WaitingBoard(Board board, Task task, List<OpenDecision> decisions) {

    public WaitingBoard {
        decisions = List.copyOf(decisions);
    }

    public boolean waitsOnTheCaptain() {
        return board.waitsOnTheCaptain() || !decisions.isEmpty();
    }

    public int count() {
        return (board.waitsOnTheCaptain() ? 1 : 0) + decisions.size();
    }
}
