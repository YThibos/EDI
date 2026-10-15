package io.github.ythibos.edi.application.migration;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.adapter.fork.ForkDecision;
import io.github.ythibos.edi.adapter.fork.ForkDecisionCard;
import io.github.ythibos.edi.adapter.fork.ForkRecommendation;
import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.task.TaskId;

final class ForkDecisionMapping {

    private static final String ANSWER_LINE = "Answer: ";
    private static final String CAPTURED_FROM_A_BOARD = "captured result";

    private final MigrationNotes notes;

    ForkDecisionMapping(MigrationNotes notes) {
        this.notes = notes;
    }

    Optional<Decision> raisedDecision(ForkDecision decision, TaskId task, Instant raisedAt) {
        if (decision.number() == null || decision.number() < 1) {
            notes.add(task.value(), "carries a decision card without a decision number; the card stays in its notes");
            return Optional.empty();
        }
        DecisionNumber number = new DecisionNumber(decision.number());
        if (decision.card() == null) {
            notes.add(number.toString(), "on " + task + " has no readable card" + becauseOf(decision.unreadableCard()) + "; it is not carried");
            return Optional.empty();
        }
        try {
            return Optional.of(Decision.raised(number, task, cardOf(decision.card()), raisedAt));
        } catch (IllegalArgumentException unusable) {
            notes.add(number.toString(), "on " + task + " has a card EDI cannot carry: " + unusable.getMessage());
            return Optional.empty();
        }
    }

    Optional<Decision> answered(Decision raised, List<String> recordedAnswer, Instant answeredAt) {
        if (recordedAnswer.isEmpty()) {
            notes.add(raised.number().toString(), "on " + raised.task() + " was settled, but no answer was recorded; it is not carried");
            return Optional.empty();
        }
        String interpretation = answerLineIn(recordedAnswer);
        return Optional.of(raised.answered(new CaptainAnswer(
                String.join("\n", recordedAnswer),
                optionNamedBy(interpretation, raised.card()),
                interpretation,
                answeredAt,
                channelOf(recordedAnswer))));
    }

    private static String becauseOf(String unreadableCard) {
        return unreadableCard == null ? "" : " (" + unreadableCard + ")";
    }

    private static DecisionCard cardOf(ForkDecisionCard card) {
        return new DecisionCard(
                textOf(card.title()),
                textOf(card.context()),
                card.options().stream().map(option -> new DecisionOption(OptionKey.parse(textOf(option.key())), textOf(option.text()))).toList(),
                recommendationOf(card.recommend()));
    }

    private static String answerLineIn(List<String> recordedAnswer) {
        return recordedAnswer.stream()
                .filter(line -> line.startsWith(ANSWER_LINE))
                .map(line -> line.substring(ANSWER_LINE.length()).strip())
                .findFirst()
                .orElse("");
    }

    private static OptionKey optionNamedBy(String interpretation, DecisionCard card) {
        String firstWord = interpretation.split("\\s", 2)[0];
        if (firstWord.length() != 1 || !AsciiCharacters.isUpperCaseLetter(firstWord.charAt(0))) {
            return null;
        }
        OptionKey key = OptionKey.parse(firstWord);
        return card.offers(key) ? key : null;
    }

    private static AnswerChannel channelOf(List<String> recordedAnswer) {
        return recordedAnswer.stream().anyMatch(line -> line.contains(CAPTURED_FROM_A_BOARD)) ? AnswerChannel.BOARD : AnswerChannel.CHAT;
    }

    private static String textOf(String value) {
        return value == null ? "" : value;
    }

    private static Recommendation recommendationOf(ForkRecommendation recommendation) {
        if (recommendation == null) {
            throw new IllegalArgumentException("it recommends no option");
        }
        return new Recommendation(OptionKey.parse(textOf(recommendation.key())), textOf(recommendation.why()));
    }
}
