package io.github.ythibos.edi.application.fleet;

import java.util.List;

public record WaitingOnTheCaptain(List<OpenDecision> decisions, List<WaitingBoard> boards, List<ReadyForReview> reviews,
        List<ChangingRequest> changing) {

    public WaitingOnTheCaptain {
        decisions = List.copyOf(decisions);
        boards = List.copyOf(boards);
        reviews = List.copyOf(reviews);
        changing = List.copyOf(changing);
    }

    public int count() {
        return decisions.size() + boards.stream().mapToInt(WaitingBoard::count).sum() + reviews.size() + changing.size();
    }

    public boolean isEmpty() {
        return count() == 0;
    }
}
