package io.github.ythibos.edi.application.fleet;

import java.util.List;

public record WaitingOnTheCaptain(List<OpenDecision> decisions, List<WaitingBoard> boards, List<ReadyForReview> reviews,
        List<WorkerQuestion> workerQuestions) {

    public WaitingOnTheCaptain {
        decisions = List.copyOf(decisions);
        boards = List.copyOf(boards);
        reviews = List.copyOf(reviews);
        workerQuestions = List.copyOf(workerQuestions);
    }

    public int count() {
        return decisions.size() + boards.size() + reviews.size();
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    public boolean hasNothingWaiting() {
        return isEmpty() && workerQuestions.isEmpty();
    }
}
