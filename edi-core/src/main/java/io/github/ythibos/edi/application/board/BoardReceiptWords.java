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
                + (arrived == 1 ? decisionsNamedByOne(promptsPerDecision) : decisionsNamedByMany(promptsPerDecision))
                + lastOneCounting(promptsPerDecision) + promiseToTellAnUnrecordedAnswer(arrived);
    }

    private static String countOf(int arrived) {
        return arrived == 1 ? "1 answer" : arrived + " answers";
    }

    private static String decisionsNamedByOne(Map<DecisionNumber, List<BoardPrompt>> promptsPerDecision) {
        return promptsPerDecision.isEmpty() ? "It names no decision." : "It names " + namesOf(promptsPerDecision.keySet().stream()) + ".";
    }

    private static String decisionsNamedByMany(Map<DecisionNumber, List<BoardPrompt>> promptsPerDecision) {
        if (promptsPerDecision.isEmpty()) {
            return "None of them names a decision.";
        }
        int naming = promptsPerDecision.values().stream().mapToInt(List::size).sum();
        return naming + " of them " + (naming == 1 ? "names " : "name ") + namesOf(promptsPerDecision.keySet().stream()) + ".";
    }

    private static String lastOneCounting(Map<DecisionNumber, List<BoardPrompt>> promptsPerDecision) {
        List<DecisionNumber> answeredTwice = promptsPerDecision.entrySet().stream().filter(answered -> answered.getValue().size() > 1)
                .map(Map.Entry::getKey).toList();
        return answeredTwice.isEmpty() ? "" : " The last one counts for " + namesOf(answeredTwice.stream()) + ".";
    }

    private static String promiseToTellAnUnrecordedAnswer(int arrived) {
        return arrived == 1 ? " If I cannot record it, I tell you in the session." : " If I cannot record one of them, I tell you in the session.";
    }

    private static String namesOf(Stream<DecisionNumber> decisions) {
        return decisions.map(DecisionNumber::toString).collect(Collectors.joining(", "));
    }
}
