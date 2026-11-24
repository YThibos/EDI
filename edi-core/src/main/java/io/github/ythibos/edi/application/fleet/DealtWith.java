package io.github.ythibos.edi.application.fleet;

import java.util.List;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.task.Task;

public record DealtWith(List<Answered> decisions, List<EndedBoard> boards, List<SettledRequest> reviews) {

    public static final DealtWith NOTHING = new DealtWith(List.of(), List.of(), List.of());

    public DealtWith {
        decisions = List.copyOf(decisions);
        boards = List.copyOf(boards);
        reviews = List.copyOf(reviews);
    }

    public int count() {
        return decisions.size() + boards.size() + reviews.size();
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    public record Answered(Decision decision, Task task) {
    }

    public record EndedBoard(Board board, Task task) {
    }

    public record SettledRequest(Task task, Delivery delivery) {
    }
}
