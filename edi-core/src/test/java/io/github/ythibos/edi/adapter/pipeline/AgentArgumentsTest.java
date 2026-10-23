package io.github.ythibos.edi.adapter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;

@Tag("adapter")
class AgentArgumentsTest {

    private static final Route OPUS_HIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH);
    private static final String OTHER_SETTINGS = """
            # no-mistakes global configuration
            agent: auto

            # Extra native agent CLI flags (optional, global only)
            # agent_args_override:
            #   codex:
            #     - -m
            """;
    private static final String PROVIDERS = """
            providers:
              gitlab:
                draft_pull_requests: true
            """;

    @TempDir
    private Path home;

    @Test
    void replacesTheClaudeCodeArgumentsAndKeepsEverythingElse() throws IOException {
        Path configuration = written(OTHER_SETTINGS + """

                # Set by hand until EDI owned it.
                agent_args_override:
                  codex:
                    - -m
                    - gpt-5.4
                  claude:
                    - --model
                    - claude-sonnet-5-5

                """ + PROVIDERS);

        AgentArguments.routeClaudeCodeIn(configuration, OPUS_HIGH);

        assertThat(configuration).content().isEqualTo(OTHER_SETTINGS + """

                # Set by hand until EDI owned it.
                agent_args_override:
                  codex:
                    - -m
                    - gpt-5.4
                  claude:
                    - --model
                    - claude-opus-5-5
                    - --effort
                    - high

                """ + PROVIDERS);
        assertThat(Files.getPosixFilePermissions(configuration)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void replacesTheWholeEntryWhenACommentInterruptsItAndKeepsTheCommentsAfterIt() throws IOException {
        Path configuration = written(OTHER_SETTINGS + """
                agent_args_override:
                  codex:
                    - -m
                    - gpt-5.4
                # Set by hand until EDI owned it.
                  claude:
                    - --model
                    - claude-sonnet-5-5
                  # The stop-gap ends here.

                # Where pull requests open.
                """ + PROVIDERS);

        AgentArguments.routeClaudeCodeIn(configuration, OPUS_HIGH);

        assertThat(configuration).content().isEqualTo(OTHER_SETTINGS + """
                agent_args_override:
                  codex:
                    - -m
                    - gpt-5.4
                  claude:
                    - --model
                    - claude-opus-5-5
                    - --effort
                    - high
                  # The stop-gap ends here.

                # Where pull requests open.
                """ + PROVIDERS);
    }

    @Test
    void addsTheClaudeCodeArgumentsToAConfigurationWithoutAny() throws IOException {
        Path configuration = written(OTHER_SETTINGS + PROVIDERS);

        AgentArguments.routeClaudeCodeIn(configuration, new Route(new ModelId("claude-haiku-5-5"), null));

        assertThat(configuration).content().isEqualTo(OTHER_SETTINGS + PROVIDERS + """

                agent_args_override:
                  claude:
                    - --model
                    - claude-haiku-5-5
                """);
    }

    @Test
    void leavesAConfigurationThatAlreadyRoutesClaudeCodeUntouched() throws IOException {
        String routed = OTHER_SETTINGS + """
                agent_args_override:
                  claude:
                    - --model
                    - claude-opus-5-5
                    - --effort
                    - high
                """;
        Path configuration = written(routed);

        AgentArguments.routeClaudeCodeIn(configuration, OPUS_HIGH);

        assertThat(configuration).content().isEqualTo(routed);
    }

    @Test
    void writesNoConfigurationThePipelineHasNotCreated() {
        Path configuration = home.resolve("config.yaml");

        AgentArguments.routeClaudeCodeIn(configuration, OPUS_HIGH);

        assertThat(configuration).doesNotExist();
    }

    private Path written(String content) throws IOException {
        Path configuration = Files.writeString(home.resolve("config.yaml"), content);
        Files.setPosixFilePermissions(configuration, PosixFilePermissions.fromString("rw-------"));
        return configuration;
    }
}
