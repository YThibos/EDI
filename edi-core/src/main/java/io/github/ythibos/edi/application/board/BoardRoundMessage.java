package io.github.ythibos.edi.application.board;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.domain.board.BoardAttachment;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;

final class BoardRoundMessage {

    private static final String INDENT = "  ";
    private static final String FREEFORM = "message";

    private BoardRoundMessage() {
    }

    static String of(Board board, BoardFeedback.Answered round, List<BoardAnswer> answers) {
        List<String> lines = new ArrayList<>();
        lines.add("The captain answered on your research board " + board.file() + ", round " + board.rounds() + ".");
        round.prompts().forEach(prompt -> lines.addAll(promptLines(prompt)));
        List<BoardAnswer> recorded = answers.stream().filter(answer -> !answer.isRefused()).toList();
        if (!recorded.isEmpty()) {
            lines.add("EDI records these as the captain's answers: " + recorded.stream().map(BoardRoundMessage::answerWords).collect(Collectors.joining("; "))
                    + ".");
        }
        answers.stream().filter(BoardAnswer::isRefused).forEach(refused -> lines.add(refusalWords(refused)));
        if (round.sessionEnded()) {
            lines.add("The captain ended the board session, so EDI no longer listens to it.");
        }
        return String.join("\n", lines);
    }

    private static List<String> promptLines(BoardPrompt prompt) {
        List<String> lines = new ArrayList<>();
        lines.add("Prompt " + prompt.uid() + " (" + whereOf(prompt) + "): " + indented(prompt.verbatim()));
        if (!prompt.tag().equals(FREEFORM) && !prompt.text().isEmpty() && !prompt.text().equals(prompt.verbatim())) {
            lines.add(INDENT + "on: " + indented(prompt.text()));
        }
        if (!prompt.context().isEmpty()) {
            lines.add(INDENT + "context: " + prompt.context().entrySet().stream().map(field -> field.getKey() + "=" + field.getValue())
                    .collect(Collectors.joining(", ")));
        }
        prompt.attachments().forEach(attachment -> lines.add(INDENT + "attachment: " + attachmentWords(attachment)));
        return lines;
    }

    private static String whereOf(BoardPrompt prompt) {
        return prompt.selector().isEmpty() ? prompt.tag() : prompt.tag() + ", at " + prompt.selector();
    }

    private static String indented(String text) {
        return text.replace("\n", "\n" + INDENT + INDENT);
    }

    private static String attachmentWords(BoardAttachment attachment) {
        return attachment.mime().isEmpty() ? attachment.path() : attachment.path() + " (" + attachment.mime() + ")";
    }

    private static String answerWords(BoardAnswer answer) {
        return answer.option() == null ? answer.decision() + " in the captain's words" : answer.decision() + " option " + answer.option();
    }

    private static String refusalWords(BoardAnswer refused) {
        return "EDI refused the board answer to " + refused.decision() + ": " + refused.refusedBecause() + ".";
    }
}
