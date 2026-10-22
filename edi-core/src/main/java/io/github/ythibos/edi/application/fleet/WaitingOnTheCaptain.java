package io.github.ythibos.edi.application.fleet;

import java.util.List;

public record WaitingOnTheCaptain(List<OpenDecision> decisions, List<WaitingBoard> boards, List<ReadyForReview> reviews) {

    public WaitingOnTheCaptain {
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
}
