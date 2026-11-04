package io.github.ythibos.edi.web.board;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.board.AnsweredRounds;
import io.github.ythibos.edi.application.board.AnsweredRounds.Round;
import io.github.ythibos.edi.domain.board.BoardAnswer;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class BoardRoundsText implements TextPresenter<AnsweredRounds> {

    private static final String INDENT = "  ";

    private final Moments moments;

    BoardRoundsText(Moments moments) {
        this.moments = moments;
    }

    @Override
    public Class<AnsweredRounds> presents() {
        return AnsweredRounds.class;
    }

    @Override
    public String text(AnsweredRounds shown) {
        if (shown.rounds().isEmpty()) {
            return "No research board of " + shown.task() + " has been answered yet.\n";
        }
        List<String> lines = new ArrayList<>(List.of(Words.counted("Board rounds answered on " + shown.task(), shown.rounds().size()) + ":"));
        shown.rounds().forEach(round -> lines.addAll(roundLines(round)));
        return String.join("\n", lines) + "\n";
    }

    private List<String> roundLines(Round round) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Board " + round.board().value() + " (" + round.file() + "), round " + round.number() + ", answered " + moments.minute(round.answeredAt())
                + ":");
        round.prompts().lines().forEach(line -> lines.add(INDENT + line));
        round.answers().forEach(answer -> lines.add(INDENT + answerLine(answer)));
        return lines;
    }

    private static String answerLine(BoardAnswer answer) {
        String chosen = answer.option() == null ? answer.decision() + " in the captain's words" : answer.decision() + " option " + answer.option();
        if (answer.refusedBecause() != null) {
            return chosen + ": refused, " + answer.refusedBecause();
        }
        return chosen + (answer.settledAt() == null ? ": waiting to settle" : ": settled as the decision's answer");
    }
}
