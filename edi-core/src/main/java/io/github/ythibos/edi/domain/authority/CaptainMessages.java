package io.github.ythibos.edi.domain.authority;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.Refusal;

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

    public boolean hold(String words) {
        Passage cited = Passage.of(words);
        return messages.stream().anyMatch(message -> passagesOf(message).anyMatch(cited::equals));
    }

    public List<DialogAnswer> dialogAnswers() {
        return messages.stream().filter(DialogAnswer.class::isInstance).map(DialogAnswer.class::cast).toList();
    }

    private CaptainMessage numbered(int number) {
        return messages.stream()
                .filter(message -> message.number() == number)
                .findFirst()
                .orElseThrow(() -> new Refusal("The captain sent no message " + number + " in this session; edi captain messages lists them."));
    }

    private static CitedWords wholeOf(CaptainMessage message) {
        if (message.holdsPastedContent()) {
            throw new Refusal("Message " + message.number() + " holds pasted content, which is never the captain's word;"
                    + " cite one line the captain typed with --line.");
        }
        return new CitedWords(message.wholeText(), message);
    }

    private static CitedWords lineOf(CaptainMessage message, int number) {
        MessageLine line = message.lines().stream()
                .filter(candidate -> candidate.number() == number)
                .findFirst()
                .orElseThrow(() -> new Refusal("Message " + message.number() + " has no line " + number + "."));
        if (!line.isCitable()) {
            throw new Refusal("Line " + number + " of message " + message.number() + " is " + (line.pasted() ? "pasted" : "blank")
                    + ", so it is not the captain's word.");
        }
        return new CitedWords(line.text().strip(), message);
    }

    private CitedWords matching(String words) {
        Passage cited = Passage.of(words);
        return messages.stream()
                .filter(message -> passagesOf(message).anyMatch(cited::equals))
                .max(typedBeforeDialogAnswersThenLatest())
                .map(message -> new CitedWords(words.strip(), message))
                .orElseThrow(() -> new Refusal("The captain did not type \"" + words.strip() + "\" in this session; cite their message with"
                        + " --message <n>, or one line of it with --line, as edi captain messages lists them."));
    }

    private static Stream<Passage> passagesOf(CaptainMessage message) {
        Stream<Passage> whole = Optional.of(message).filter(candidate -> !candidate.holdsPastedContent())
                .map(candidate -> Passage.of(candidate.wholeText())).stream();
        Stream<Passage> lines = message.lines().stream().filter(MessageLine::isCitable).map(line -> Passage.of(line.text()));
        return Stream.concat(whole, lines);
    }

    private static Comparator<CaptainMessage> typedBeforeDialogAnswersThenLatest() {
        return Comparator.comparing((CaptainMessage message) -> message instanceof TypedMessage).thenComparingInt(CaptainMessage::number);
    }
}
