package io.github.ythibos.edi.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BraceExpansionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "core.{token,lock}      | core.token core.lock",
        "core.toke{m..o}        | core.tokem core.token core.tokeo",
        "st{ate/core.tok,x}en   | state/core.token stxen",
        "lamp{1..10..4}         | lamp1 lamp5 lamp9",
        "lamp{01..03}           | lamp01 lamp02 lamp03",
        "lamp{c..a}             | lampc lampb lampa",
        "{a,b{c,d}}             | a bc bd",
        "{a{b,c}                | {ab {ac",
        "core.{,to}ken          | core.ken core.token",
        "${HOME}/lamp           | ${HOME}/lamp",
        "\\{a,b}                | \\{a,b}",
        "{a\\,b}                | {a\\,b}",
        "{lamp}                 | {lamp}"})
    void expandsBracesAsBashDoes(String pattern, String words) {
        assertThat(BraceExpansion.of(pattern, 64)).containsExactly(words.split(" "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{1..5000}", "{a,b}{a,b}{a,b}{a,b}{a,b}{a,b}{a,b}"})
    void cannotTraceMoreWordsThanItFollows(String pattern) {
        assertThatThrownBy(() -> BraceExpansion.of(pattern, 64)).isInstanceOf(UntraceablePaths.class)
                .hasMessage("its braces expand to more than 64 words");
    }

    @Test
    void leavesAWordWithoutBracesAsItIs() {
        assertThat(BraceExpansion.of("src/Lamp.java", 64)).isEqualTo(List.of("src/Lamp.java"));
    }
}
