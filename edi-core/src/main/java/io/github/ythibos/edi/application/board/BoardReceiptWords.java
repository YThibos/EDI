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
        return "Received " + arrived(round) + " in round " + board.rounds() + ". " + routing(answers);
    }

    private static String arrived(BoardFeedback.Answered round) {
        int count = round.prompts().size();
        return count == 1 ? "1 answer" : count + " answers";
    }

    private static String routing(List<BoardAnswer> answers) {
        if (answers.isEmpty()) {
            return "None of them answers a decision.";
        }
        return "They go to " + answers.stream().map(answer -> answer.decision().toString()).distinct().collect(Collectors.joining(", ")) + ".";
    }
}
