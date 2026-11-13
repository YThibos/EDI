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
        Turn turn = recorded.recorded();
        return "Recorded. This turn's report holds " + Words.quantity(turn.done().size(), "done item") + " and "
                + Words.quantity(turn.next().size(), "next item") + "; the stop hands it to you to paste.\n";
    }
}
