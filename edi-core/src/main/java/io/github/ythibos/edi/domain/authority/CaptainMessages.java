package io.github.ythibos.edi.domain.authority;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

public record CaptainMessages(List<CaptainMessage> messages) {

    public CaptainMessages {
        messages = List.copyOf(messages);
    }

    public CitedWords cite(Citation citation) {
        return switch (citation) {
            case Citation.WholeMessage whole -> wholeOf(numbered(whole.number()));
            case Citation.OneLine line -> lineOf(numbered(line.message()), line.line());
            case Citation.Verbatim text -> matching(text.words());
        };
    }

    public boolean typed(String words) {
        return messages.stream().filter(TypedMessage.class::isInstance).flatMap(CaptainMessages::citableWordsOf).anyMatch(saying(words));
    }

    public List<DialogAnswer> dialogAnswers() {
        return messages.stream().filter(DialogAnswer.class::isInstance).map(DialogAnswer.class::cast).toList();
    }

    private CaptainMessage numbered(int number) {
        return messages.stream()
                .filter(message -> message.number() == number)
                .findFirst()
                .orElseThrow(() -> new Refusal(Rule.TYPED_WORDS, "The captain sent no message " + number + " in this session; edi captain messages lists them."));
    }

    private static CitedWords wholeOf(CaptainMessage message) {
        if (message.holdsPastedContent()) {
            throw new Refusal(Rule.TYPED_WORDS, "Message " + message.number() + " holds pasted content, which is never the captain's word;"
                    + " cite one line the captain typed with --line.");
        }
        return new CitedWords(message.wholeText(), message);
    }

    private static CitedWords lineOf(CaptainMessage message, int number) {
        MessageLine line = message.lines().stream()
                .filter(candidate -> candidate.number() == number)
                .findFirst()
                .orElseThrow(() -> new Refusal(Rule.TYPED_WORDS, "Message " + message.number() + " has no line " + number + "."));
        if (!line.isCitable()) {
            throw new Refusal(Rule.TYPED_WORDS, "Line " + number + " of message " + message.number() + " is " + (line.pasted() ? "pasted" : "blank")
                    + ", so it is not the captain's word.");
        }
        return new CitedWords(line.text().strip(), message);
    }

    private CitedWords matching(String words) {
        return messages.stream()
                .flatMap(CaptainMessages::citableWordsOf)
                .filter(saying(words))
                .max(typedBeforeDialogAnswersThenLatest())
                .orElseThrow(() -> new Refusal(Rule.TYPED_WORDS, "The captain did not type \"" + words.strip() + "\" in this session; cite their message with"
                        + " --message <n>, or one line of it with --line, as edi captain messages lists them."));
    }

    private static Stream<CitedWords> citableWordsOf(CaptainMessage message) {
        Stream<CitedWords> whole = Optional.of(message).filter(candidate -> !candidate.holdsPastedContent())
                .map(candidate -> new CitedWords(candidate.wholeText(), candidate)).stream();
        Stream<CitedWords> lines = message.lines().stream().filter(MessageLine::isCitable).map(line -> new CitedWords(line.text().strip(), message));
        return Stream.concat(whole, lines);
    }

    private static Predicate<CitedWords> saying(String words) {
        Passage cited = Passage.of(words);
        return stored -> Passage.of(stored.verbatim()).equals(cited);
    }

    private static Comparator<CitedWords> typedBeforeDialogAnswersThenLatest() {
        return Comparator.comparing((CitedWords cited) -> cited.source() instanceof TypedMessage).thenComparingInt(cited -> cited.source().number());
    }
}
