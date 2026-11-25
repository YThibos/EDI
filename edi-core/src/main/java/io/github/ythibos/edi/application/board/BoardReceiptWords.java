package io.github.ythibos.edi.application.board;

import java.util.List;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.board.BoardFeedback;

final class BoardReceiptWords {

    private BoardReceiptWords() {
    }

    static String of(Board board, BoardFeedback.Answered round, List<BoardAnswer> answers) {
        int arrived = round.prompts().size();
        return "Received " + countOf(arrived) + " in round " + board.rounds() + ". " + (arrived == 1 ? routingOfOne(answers) : routingOfMany(answers));
    }

    private static String countOf(int arrived) {
        return arrived == 1 ? "1 answer" : arrived + " answers";
    }

    private static String routingOfOne(List<BoardAnswer> answers) {
        return answers.isEmpty() ? "It answers no decision." : "It goes to " + decisionsOf(answers) + ".";
    }

    private static String routingOfMany(List<BoardAnswer> answers) {
        if (answers.isEmpty()) {
            return "None of them answers a decision.";
        }
        return answers.size() + " of them " + (answers.size() == 1 ? "goes" : "go") + " to " + decisionsOf(answers) + ".";
    }

    private static String decisionsOf(List<BoardAnswer> answers) {
        return answers.stream().map(answer -> answer.decision().toString()).collect(Collectors.joining(", "));
    }
}
