package io.github.ythibos.edi.web.decision;

import java.util.ArrayList;
import java.util.List;

import io.github.ythibos.edi.domain.decision.CaptainAnswer;
import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Words;

public final class DecisionLines {

    private static final String INDENT = "  ";

    private DecisionLines() {
    }

    public static List<String> of(Decision decision) {
        List<String> lines = new ArrayList<>();
        lines.add(decision.number() + "  " + decision.task() + "  " + decision.card().title());
        lines.addAll(cardLines(decision.card()));
        if (!decision.isOpen()) {
            lines.addAll(answerLines(decision.answer()));
        }
        return lines;
    }

    private static List<String> cardLines(DecisionCard card) {
        List<String> lines = new ArrayList<>();
        if (!card.context().isEmpty()) {
            lines.add(Words.indented(card.context(), INDENT));
        }
        card.options().forEach(option -> lines.add(INDENT + "(" + option.key() + ") " + option.text()));
        lines.add(INDENT + "Recommended: " + card.recommendation().key() + " - " + card.recommendation().why());
        return lines;
    }

    private static List<String> answerLines(CaptainAnswer answer) {
        List<String> lines = new ArrayList<>();
        String option = answer.choosesAnOption() ? "option " + answer.option() + ", " : "";
        lines.add(INDENT + "Answered " + Moments.minute(answer.answeredAt()) + " (" + option + answer.channel().word() + "):");
        lines.add(Words.indented(answer.verbatim(), INDENT + INDENT));
        if (!answer.interpretation().isEmpty()) {
            lines.add(INDENT + "Interpretation: " + answer.interpretation());
        }
        return lines;
    }
}
