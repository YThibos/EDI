package io.github.ythibos.edi.web.report;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class RecordedTurnText implements TextPresenter<RecordedTurn> {

    @Override
    public Class<RecordedTurn> presents() {
        return RecordedTurn.class;
    }

    @Override
    public String text(RecordedTurn recorded) {
        return "Recorded. This turn's report holds " + contents(recorded.recorded()) + "; the stop hands it to you to paste.\n";
    }

    private static String contents(Turn turn) {
        String doneItems = Words.quantity(turn.done().size(), "done item");
        String nextItems = Words.quantity(turn.next().size(), "next item");
        return turn.tasks().isEmpty() ? doneItems + " and " + nextItems : doneItems + ", " + nextItems + " and " + rewordedTasks(turn);
    }

    private static String rewordedTasks(Turn turn) {
        return Words.quantity(turn.tasks().size(), "reworded task");
    }
}
