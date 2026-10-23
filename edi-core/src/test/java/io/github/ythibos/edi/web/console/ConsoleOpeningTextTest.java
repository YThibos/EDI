package io.github.ythibos.edi.web.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.access.OpenConsole.ConsoleOpening;

class ConsoleOpeningTextTest {

    private static final URI CONSOLE = URI.create("http://127.0.0.1:47318/");

    @Test
    void namesThePageTheSessionsStartOpenedInsteadOfOpeningAnother() {
        String text = new ConsoleOpeningText().text(ConsoleOpening.openedAtSessionStart(CONSOLE));

        assertThat(text).isEqualTo("EDI console: " + CONSOLE + " is already open in your browser; it opened when this session started.\n");
    }
}
