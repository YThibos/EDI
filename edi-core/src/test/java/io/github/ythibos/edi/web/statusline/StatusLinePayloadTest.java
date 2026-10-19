package io.github.ythibos.edi.web.statusline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.statusline.ClaudeSessionStatus;
import io.github.ythibos.edi.domain.statusline.SessionTokens;
import io.github.ythibos.edi.domain.statusline.UsageLimit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class StatusLinePayloadTest {

    @Test
    void readsFractionalPercentagesDownToAWholeOneAndAWindowWithoutItsReset() {
        ClaudeSessionStatus session = StatusLinePayload.sessionIn(payload("""
                {"model":{"display_name":"Sonnet 5.5"},"context_window":{"used_percentage":41.9,"total_input_tokens":1200,"total_output_tokens":300,
                 "current_usage":null},"rate_limits":{"five_hour":{"used_percentage":23.5},"seven_day":{"used_percentage":99.99,"resets_at":1792396800}}}"""));

        assertThat(session).isEqualTo(new ClaudeSessionStatus(Optional.of("Sonnet 5.5"), Optional.empty(), OptionalInt.of(41), new SessionTokens(1500, 0, 0),
                Optional.of(new UsageLimit(23, Optional.empty())), Optional.of(new UsageLimit(99, Optional.of(Instant.ofEpochSecond(1792396800))))));
    }

    @Test
    void readsNothingFromAnEmptyPayload() {
        ClaudeSessionStatus session = StatusLinePayload.sessionIn(payload("{\"model\":{\"display_name\":\" \"},\"effort\":null}"));

        assertThat(session).isEqualTo(new ClaudeSessionStatus(Optional.empty(), Optional.empty(), OptionalInt.empty(), new SessionTokens(0, 0, 0),
                Optional.empty(), Optional.empty()));
    }

    @Test
    void takesTheWorkspaceDirectoryBeforeTheShellsAndRefusesAPayloadWithNeither() {
        assertThat(StatusLinePayload.workingDirectoryIn(payload("{\"cwd\":\"/home/example/shell\",\"workspace\":{\"current_dir\":\"/home/example/harness\"}}")))
                .isEqualTo(Path.of("/home/example/harness"));
        assertThat(StatusLinePayload.workingDirectoryIn(payload("{\"cwd\":\"/home/example/shell\"}"))).isEqualTo(Path.of("/home/example/shell"));
        assertThatThrownBy(() -> StatusLinePayload.workingDirectoryIn(payload("{}")))
                .hasMessage("A status line names its working directory in workspace.current_dir or cwd.");
    }

    private static JsonNode payload(String json) {
        return new JsonMapper().readTree(json);
    }
}
