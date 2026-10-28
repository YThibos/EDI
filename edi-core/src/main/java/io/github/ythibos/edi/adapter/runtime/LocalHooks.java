package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.ythibos.edi.domain.Refusal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class LocalHooks {

    private static final Path LOCAL_SETTINGS = Path.of(".claude", "settings.local.json");

    private LocalHooks() {
    }

    static void refuseHooksIn(Path worktree, JsonMapper json) {
        Path localSettings = worktree.resolve(LOCAL_SETTINGS);
        if (!Files.isRegularFile(localSettings)) {
            return;
        }
        if (definesHooks(localSettings, json)) {
            throw new Refusal(localSettings + " defines hooks, which Claude Code would run beside the hooks a worker gets at launch; remove them before launching a worker there.");
        }
    }

    private static boolean definesHooks(Path localSettings, JsonMapper json) {
        try {
            JsonNode hooks = json.readTree(Files.readString(localSettings)).path("hooks");
            return !hooks.isMissingNode() && !hooks.isEmpty();
        } catch (JacksonException unreadable) {
            throw new Refusal(localSettings + " is not JSON Claude Code can read, so whether it defines hooks is unclear; fix or remove it.");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
