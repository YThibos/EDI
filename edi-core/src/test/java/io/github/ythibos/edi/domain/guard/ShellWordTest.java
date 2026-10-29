package io.github.ythibos.edi.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ShellWordTest {

    @Test
    void keepsWhatTheShellExpandsApartFromWhatItTakesAsWritten() {
        Map<String, String> patterns = Map.of(
                "cat ~/lamp*", "~/lamp*",
                "cat '~/lamp*'", "\\~/lamp\\*",
                "cat \"$HOME/lamp\"*", "$HOME/lamp*",
                "cat \"${HOME}/lamp\"", "${HOME}/lamp",
                "cat '$HOME/lamp'", "\\$HOME/lamp",
                "cat lamp\\*", "lamp\\*",
                "cat 'lamp.{java,md}'", "lamp.\\{java\\,md\\}",
                "cat core.tok@(e|a)n", "core.tok@(e|a)n",
                "cat core.(tok|x)en", "core.(tok|x)en");

        patterns.forEach((command, pattern) -> assertThat(ShellScript.commandsIn(command).getFirst().arguments().getLast().pattern())
                .as(command).isEqualTo(pattern));
    }

    @Test
    void writesTheTextAsTheProgramReceivesIt() {
        assertThat(ShellScript.commandsIn("cat '~/lamp*' core.tok@(e|a)n").getFirst().argumentTexts()).containsExactly("~/lamp*", "core.tok@(e|a)n");
    }
}
