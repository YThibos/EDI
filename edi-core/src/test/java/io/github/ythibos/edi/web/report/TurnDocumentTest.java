package io.github.ythibos.edi.web.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.ythibos.edi.domain.report.TaskWording;
import io.github.ythibos.edi.domain.report.Turn;
import io.github.ythibos.edi.domain.report.TurnItem;
import tools.jackson.databind.json.JsonMapper;

class TurnDocumentTest {

    private static final JsonMapper JSON = new JsonMapper();

    @Test
    void readsWhatTheTurnDidWhatComesNextAndHowTasksAreWorded() {
        Turn turn = read("""
                {"done": [{"projects": ["harness"], "text": "Fitted the lamp"}],
                 "next": [{"text": "Choose the paint"}],
                 "tasks": {"lantern": {"text": "Rework the brass lantern", "state": "running", "projects": []},
                           "horn": {"state": null}}}""");

        assertThat(turn.done()).containsExactly(new TurnItem(List.of("harness"), "Fitted the lamp"));
        assertThat(turn.next()).containsExactly(new TurnItem(List.of(), "Choose the paint"));
        assertThat(turn.tasks())
                .containsEntry("lantern", new TaskWording("Rework the brass lantern", "running", List.of()))
                .containsEntry("horn", new TaskWording(null, null, null));
    }

    @Test
    void readsATurnThatDidNothingAsNoTurn() {
        assertThat(read("{}")).isEqualTo(Turn.NONE);
        assertThat(read("{\"done\": null, \"next\": [], \"tasks\": null}")).isEqualTo(Turn.NONE);
    }

    @ParameterizedTest
    @MethodSource("malformedTurns")
    void refusesAMalformedTurnInOneSentence(String turn, String sentence) {
        assertThatThrownBy(() -> read(turn)).isInstanceOf(IllegalArgumentException.class).hasMessage(sentence);
    }

    static Stream<Arguments> malformedTurns() {
        return Stream.of(
                Arguments.of("[]", "The turn is one JSON object with done, next and tasks."),
                Arguments.of("{\"done\": [{\"text\": \"x\"}], \"extra\": 1, \"another\": 2}", "The turn takes only done, next and tasks, not another, extra."),
                Arguments.of("{\"done\": {}}", "The turn's done is a list of items."),
                Arguments.of("{\"next\": [\"Fit the lamp\"]}", "Each item of the turn's next is an object with projects and text."),
                Arguments.of("{\"done\": [{\"text\": \"x\", \"why\": \"y\"}]}", "An item of the turn's done takes only projects and text, not why."),
                Arguments.of("{\"next\": [{\"text\": \"two\\nlines\"}]}", "The text of an item of the turn's next is one non-empty line."),
                Arguments.of("{\"done\": [{\"projects\": []}]}", "The text of an item of the turn's done is one non-empty line."),
                Arguments.of("{\"done\": [{\"projects\": \"harness\", \"text\": \"x\"}]}", "The projects of an item of the turn's done are a list."),
                Arguments.of("{\"done\": [{\"projects\": [\"\"], \"text\": \"x\"}]}", "Each project of an item of the turn's done is one non-empty line."),
                Arguments.of("{\"tasks\": []}", "The turn's tasks are an object keyed by task id."),
                Arguments.of("{\"tasks\": {\"lantern\": \"x\"}}", "The turn's wording of lantern is an object with text, state and projects."),
                Arguments.of("{\"tasks\": {\"lantern\": {\"title\": \"x\"}}}", "The turn's wording of lantern takes only text, state and projects, not title."),
                Arguments.of("{\"tasks\": {\"lantern\": {\"state\": \"a\\rb\"}}}", "The state of the turn's wording of lantern is one non-empty line."),
                Arguments.of("{\"tasks\": {\"lantern\": {\"text\": 3}}}", "The text of the turn's wording of lantern is one non-empty line."));
    }

    private static Turn read(String turn) {
        return TurnDocument.read(JSON.readTree(turn));
    }
}
