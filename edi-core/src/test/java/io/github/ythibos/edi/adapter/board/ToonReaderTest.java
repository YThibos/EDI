package io.github.ythibos.edi.adapter.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ToonReaderTest {

    private final JsonMapper json = new JsonMapper();

    @Test
    void readsNestedObjectsAndTypedPrimitives() {
        JsonNode read = ToonReader.read("""
                session:
                  file: /tmp/board.html
                  ended: true
                  rounds: 3
                  code: "05"
                  owner: null
                "odd key": "a \\"quoted\\" word\\twith\\u00e9"
                """);

        assertThat(read).isEqualTo(json.readTree("""
                {"session":{"file":"/tmp/board.html","ended":true,"rounds":3,"code":"05","owner":null},"odd key":"a \\"quoted\\" word\\twith\\u00e9"}"""));
    }

    @Test
    void readsInlineArraysAndTablesWithQuotedCells() {
        JsonNode read = ToonReader.read("""
                help[2]: first hint,"second, with a comma"
                rows[2]{id,"long name"}:
                  1,plain
                  "2","with, comma"
                empty: []
                """);

        assertThat(read).isEqualTo(json.readTree("""
                {"help":["first hint","second, with a comma"],
                 "rows":[{"id":1,"long name":"plain"},{"id":"2","long name":"with, comma"}],
                 "empty":[]}"""));
    }

    @Test
    void readsListItemsWhoseFirstFieldOpensAnArrayOrAnObject() {
        JsonNode read = ToonReader.read("""
                items[4]:
                  - files[1]{path}:
                      /tmp/a.png
                    note: first
                  - owner:
                      name: scout
                    note: second
                  - [2]: 1,2
                  - plain words
                """);

        assertThat(read).isEqualTo(json.readTree("""
                {"items":[{"files":[{"path":"/tmp/a.png"}],"note":"first"},{"owner":{"name":"scout"},"note":"second"},[1,2],"plain words"]}"""));
    }

    @Test
    void refusesIndentationThatIsNotTwoSpacesDeep() {
        assertThatThrownBy(() -> ToonReader.read("session:\n   file: board.html\n"))
                .isInstanceOf(UnreadableResult.class)
                .hasMessage("Line 2 of the Lavish result cannot be read: it is indented by 3 spaces, not a multiple of 2.");
    }

    @Test
    void refusesATableRowWithTheWrongNumberOfCells() {
        assertThatThrownBy(() -> ToonReader.read("rows[1]{a,b}:\n  1,2,3\n"))
                .isInstanceOf(UnreadableResult.class)
                .hasMessage("Line 2 of the Lavish result cannot be read: the row has 3 values for the 2 fields of line 1.");
    }

    @Test
    void refusesAQuotedValueThatIsNeverClosed() {
        assertThatThrownBy(() -> ToonReader.read("prompt: \"half a sentence\n"))
                .isInstanceOf(UnreadableResult.class)
                .hasMessage("Line 1 of the Lavish result cannot be read: the quoted value \"half a sentence does not end in a quote.");
    }
}
