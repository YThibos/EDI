package io.github.ythibos.edi.application.fleet;

import java.util.List;

public record WaitingOnTheCaptain(List<OpenDecision> decisions, List<WaitingBoard> boards, List<ReadyForReview> reviews,
        List<ChangingRequest> changing, List<WorkerQuestion> workerQuestions) {

    public WaitingOnTheCaptain {
        decisions = List.copyOf(decisions);
        boards = List.copyOf(boards);
        reviews = List.copyOf(reviews);
        changing = List.copyOf(changing);
        workerQuestions = List.copyOf(workerQuestions);
    }

    public WaitingOnTheCaptain(List<OpenDecision> decisions, List<WaitingBoard> boards, List<ReadyForReview> reviews, List<ChangingRequest> changing) {
        this(decisions, boards, reviews, changing, List.of());
    }

    public int count() {
        return decisions.size() + boards.size() + reviews.size() + changing.size();
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    public boolean hasNothingWaiting() {
        return isEmpty() && workerQuestions.isEmpty();
    }
}
