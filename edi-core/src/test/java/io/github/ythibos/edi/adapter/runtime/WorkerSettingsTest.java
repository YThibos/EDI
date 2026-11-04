package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.testing.StandInScripts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WorkerSettingsTest {

    private static final JsonMapper JSON = new JsonMapper();

    @TempDir
    private Path directory;

    private Path worktree;

    @BeforeEach
    void prepareACopy() throws IOException {
        worktree = Files.createDirectories(directory.resolve("worktrees/harness/fit-lamp/.claude")).getParent();
    }

    @Test
    void refusesACopyWhoseClaudeSettingsSetTheEnvironmentOrSwitchTheHooksOff() throws IOException {
        for (String file : List.of("settings.local.json", "settings.json")) {
            for (String settings : List.of("{\"env\": {\"PATH\": \"/tmp/lamp:/usr/bin\"}}", "{\"disableAllHooks\": true}")) {
                Files.writeString(worktree.resolve(".claude").resolve(file), settings);

                assertThatThrownBy(() -> LocalHooks.refuseHooksIn(worktree, JSON)).as(file + " " + settings).isInstanceOf(Refusal.class)
                        .hasMessageContaining(file);
                Files.delete(worktree.resolve(".claude").resolve(file));
            }
        }
    }

    @Test
    void launchesInACopyWhoseClaudeSettingsLeaveTheHooksAndTheEnvironmentAlone() throws IOException {
        Files.writeString(worktree.resolve(".claude/settings.json"), "{\"attribution\": {\"commit\": \"\"}, \"disableAllHooks\": false, \"env\": {}}");
        Files.writeString(worktree.resolve(".claude/settings.local.json"), "{\"permissions\": {\"allow\": []}}");

        assertThatCode(() -> LocalHooks.refuseHooksIn(worktree, JSON)).doesNotThrowAnyException();
    }

    @Test
    void callsEdiByItsAbsolutePathFromEveryHookAWorkerGets() throws IOException {
        Path edi = Path.of("/opt/harbour's tools/bin/edi");

        JsonNode written = JSON.readTree(Files.readString(WorkerSettingsFile.writtenInto(directory.resolve("state"), edi, JSON)));

        assertThat(written.path("hooks").values()).isNotEmpty().allSatisfy(event -> assertThat(event.values()).allSatisfy(matcher -> assertThat(
                matcher.path("hooks").values()).allSatisfy(hook -> assertThat(hook.path("command").asString())
                        .startsWith("'/opt/harbour'\\''s tools/bin/edi' hook "))));
        assertThat(written.path("disableAllHooks").asBoolean(true)).isFalse();
    }

    @Test
    void findsEdiOnThePathAsTheShellWould() throws IOException {
        Path first = Files.createDirectories(directory.resolve("first"));
        Path second = StandInScripts.installed(directory.resolve("second/edi"), "#!/bin/sh\n").getParent();
        Files.writeString(first.resolve("edi"), "not executable\n");

        assertThat(EdiOnThePath.in(first + ":" + second + "::/nowhere")).contains(second.resolve("edi").toAbsolutePath());
        assertThat(EdiOnThePath.in(first.toString())).isEmpty();
        assertThat(EdiOnThePath.in(null)).isEmpty();
    }
}
