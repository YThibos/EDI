package io.github.ythibos.edi.domain.board;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.OptionKey;

public record BoardPrompt(String uid, String tag, String text, String prompt, String selector, Map<String, String> context,
        List<BoardAttachment> attachments) {

    private static final String DECISION_FIELD = "decision";
    private static final String OPTION_FIELD = "option";
    private static final String SEPARATORS = " \t\n:,;.-()";
    private static final String OPTION_ENDINGS = ",.:)";

    public BoardPrompt {
        uid = Objects.requireNonNullElse(uid, "");
        tag = Objects.requireNonNullElse(tag, "");
        text = Objects.requireNonNullElse(text, "").strip();
        prompt = Objects.requireNonNullElse(prompt, "").strip();
        selector = Objects.requireNonNullElse(selector, "");
        context = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(context));
        attachments = List.copyOf(attachments);
        if (prompt.isEmpty() && text.isEmpty()) {
            throw new IllegalArgumentException("A prompt from a board carries the captain's words or the text the answer is about.");
        }
    }

    public String verbatim() {
        return prompt.isEmpty() ? text : prompt;
    }

    public Optional<DecisionNumber> decisionNamed() {
        String named = context.containsKey(DECISION_FIELD) ? context.get(DECISION_FIELD).strip() : firstWord(verbatim());
        return DecisionNumber.isWritten(named) ? Optional.of(DecisionNumber.parse(named)) : Optional.empty();
    }

    public Optional<OptionKey> optionChosen(DecisionCard card) {
        return context.containsKey(OPTION_FIELD) ? optionIn(context.get(OPTION_FIELD).strip()) : optionTyped().filter(card::offers);
    }

    private static String firstWord(String words) {
        int start = skippedSeparators(words, 0);
        int end = start;
        while (end < words.length() && !isSeparator(words.charAt(end))) {
            end++;
        }
        return words.substring(start, end);
    }

    private static Optional<OptionKey> optionIn(String chosen) {
        return chosen.length() == 1 && AsciiCharacters.isUpperCaseLetter(chosen.charAt(0)) ? Optional.of(OptionKey.parse(chosen)) : Optional.empty();
    }

    private Optional<OptionKey> optionTyped() {
        String afterNumber = afterFirstWord(verbatim());
        String chosen = firstWord(afterNumber);
        int end = skippedSeparators(afterNumber, 0) + chosen.length();
        return end == afterNumber.length() || OPTION_ENDINGS.indexOf(afterNumber.charAt(end)) >= 0 ? optionIn(chosen) : Optional.empty();
    }

    private static String afterFirstWord(String words) {
        int start = skippedSeparators(words, 0);
        return words.substring(start + firstWord(words).length());
    }

    private static int skippedSeparators(String words, int from) {
        int position = from;
        while (position < words.length() && isSeparator(words.charAt(position))) {
            position++;
        }
        return position;
    }

    private static boolean isSeparator(char character) {
        return SEPARATORS.indexOf(character) >= 0;
    }
}
