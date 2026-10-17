package io.github.ythibos.edi.web.console;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;
import io.github.ythibos.edi.web.text.TextPresenter;

@Component
class ConsoleOpeningText implements TextPresenter<ConsoleOpening> {

    @Override
    public Class<ConsoleOpening> presents() {
        return ConsoleOpening.class;
    }

    @Override
    public String text(ConsoleOpening opening) {
        return opening.opened()
                ? "EDI console: " + opening.console() + " is open in your browser.\n"
                : "EDI console: open " + opening.signInLink() + " in your browser; the link signs you in once, within two minutes.\n";
    }
}
