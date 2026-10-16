package io.github.ythibos.edi.web.worker;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.worker.PeekWorker.Peek;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class PeekText implements TextPresenter<Peek> {

    @Override
    public Class<Peek> presents() {
        return Peek.class;
    }

    @Override
    public String text(Peek peek) {
        String lines = String.join("\n", peek.pane().lines()).stripTrailing();
        return peek.worker().id() + " shows " + peek.pane().state().word() + ":\n" + lines + "\n";
    }
}
