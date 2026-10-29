package io.github.ythibos.edi.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShellGlobTest {

    @ParameterizedTest
    @CsvSource(delimiterString = "=>", value = {
        "core.to[^x]en => core.token",
        "core.to[!x]en => core.token",
        "core.to[a-m]en => core.token",
        "core.[[:alpha:]]oken => core.token",
        "core.t[[=o=]]ken => core.token",
        "core.tok@(e|a)n => core.token",
        "core.tok!(x) => core.token",
        "core.(tok|x)en => core.token",
        "core.t?ken => core.token",
        "*.token => core.token",
        "[]x] => ]",
        "[a-] => -",
        "\\* => *",
        "café[[:alpha:]] => cafée"})
    void matchesANameAsBashDoes(String segment, String name) {
        assertThat(ShellGlob.of(segment).matcher(name).matches()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiterString = "=>", value = {
        "core.to[^k]en => core.token",
        "core.to[!k]en => core.token",
        "core.[[:digit:]]oken => core.token",
        "\\* => lamp",
        "[z-a] => z",
        "core.t?ken => core.tken"})
    void refusesANameBashWouldNotMatch(String segment, String name) {
        assertThat(ShellGlob.of(segment).matcher(name).matches()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"core.tok*", "core.t?ken", "core.to[kx]en", "core.tok@(e)n", "+(lamp)", "core.(tok|x)en"})
    void knowsAGlob(String segment) {
        assertThat(ShellGlob.isAGlob(segment)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"core.token", "core.tok\\*", "someone@example.org", "lamp+", "\\[x]"})
    void knowsAPlainName(String segment) {
        assertThat(ShellGlob.isAGlob(segment)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiterString = "=>", value = {"core.tok\\* => core.tok*", "a\\\\b => a\\b", "plain => plain"})
    void readsTheLiteralNameOfAPlainSegment(String segment, String name) {
        assertThat(ShellGlob.literal(segment)).isEqualTo(name);
    }
}
