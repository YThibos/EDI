package io.github.ythibos.edi.application.board;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;
import io.github.ythibos.edi.domain.decision.DecisionNumber;

final class BoardReceiptWords {

    private BoardReceiptWords() {
    }

    static String of(Board board, BoardFeedback.Answered round) {
        int arrived = round.prompts().size();
        Map<DecisionNumber, List<BoardPrompt>> promptsPerDecision = board.promptsPerDecision(round);
        return "Received " + countOf(arrived) + " in round " + board.rounds() + ". "
                + (arrived == 1 ? routingOfOne(promptsPerDecision) : routingOfMany(promptsPerDecision)) + lastOneCounting(promptsPerDecision);
    }

    private static String countOf(int arrived) {
        return arrived == 1 ? "1 answer" : arrived + " answers";
    }

    private static String routingOfOne(Map<DecisionNumber, List<BoardPrompt>> promptsPerDecision) {
        return promptsPerDecision.isEmpty() ? "It answers no decision." : "It goes to " + namesOf(promptsPerDecision.keySet().stream()) + ".";
    }

    private static String routingOfMany(Map<DecisionNumber, List<BoardPrompt>> promptsPerDecision) {
        if (promptsPerDecision.isEmpty()) {
            return "None of them answers a decision.";
        }
        int bound = promptsPerDecision.values().stream().mapToInt(List::size).sum();
        return bound + " of them " + (bound == 1 ? "goes" : "go") + " to " + namesOf(promptsPerDecision.keySet().stream()) + ".";
    }

    private static String lastOneCounting(Map<DecisionNumber, List<BoardPrompt>> promptsPerDecision) {
        List<DecisionNumber> answeredTwice = promptsPerDecision.entrySet().stream().filter(answered -> answered.getValue().size() > 1)
                .map(Map.Entry::getKey).toList();
        return answeredTwice.isEmpty() ? "" : " The last one counts for " + namesOf(answeredTwice.stream()) + ".";
    }

    private static String namesOf(Stream<DecisionNumber> decisions) {
        return decisions.map(DecisionNumber::toString).collect(Collectors.joining(", "));
    }
}
