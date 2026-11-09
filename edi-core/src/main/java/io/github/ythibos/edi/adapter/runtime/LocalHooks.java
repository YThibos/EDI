package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class LocalHooks {

    private static final Path LOCAL_SETTINGS = Path.of(".claude", "settings.local.json");
    private static final Path PROJECT_SETTINGS = Path.of(".claude", "settings.json");
    private static final String HOOKS = "hooks";
    private static final String ENVIRONMENT = "env";
    private static final String HOOKS_SWITCH = "disableAllHooks";

    private LocalHooks() {
    }

    static void refuseHooksIn(Path worktree, JsonMapper json) {
        Path localSettings = worktree.resolve(LOCAL_SETTINGS);
        readable(localSettings, json).ifPresent(settings -> {
            if (isSet(settings.path(HOOKS))) {
                throw new Refusal(Rule.LAUNCH_HOOKS_INTACT, localSettings + " defines hooks, which Claude Code would run beside the hooks a worker gets at launch; remove them before launching a worker there.");
            }
            refuseWhatSwitchesTheGuardOff(localSettings, settings);
        });
        Path projectSettings = worktree.resolve(PROJECT_SETTINGS);
        readable(projectSettings, json).ifPresent(settings -> refuseWhatSwitchesTheGuardOff(projectSettings, settings));
    }

    private static Optional<JsonNode> readable(Path settings, JsonMapper json) {
        if (!Files.isRegularFile(settings)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readTree(Files.readString(settings)));
        } catch (JacksonException unreadable) {
            throw new Refusal(Rule.LAUNCH_HOOKS_INTACT, settings + " is not JSON Claude Code can read, so whether it switches a worker's hooks off is unclear; fix or remove it.");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static boolean isSet(JsonNode value) {
        return !value.isMissingNode() && !value.isNull() && !value.isEmpty();
    }

    private static void refuseWhatSwitchesTheGuardOff(Path file, JsonNode settings) {
        if (isSet(settings.path(ENVIRONMENT))) {
            throw new Refusal(Rule.LAUNCH_HOOKS_INTACT, file + " sets environment variables for the session, which could point a worker's hooks or its edi command elsewhere;"
                    + " remove env from it before launching a worker there.");
        }
        JsonNode hooksSwitch = settings.path(HOOKS_SWITCH);
        if (!hooksSwitch.isMissingNode() && !(hooksSwitch.isBoolean() && !hooksSwitch.asBoolean())) {
            throw new Refusal(Rule.LAUNCH_HOOKS_INTACT, file + " sets disableAllHooks, which would switch off the hooks that guard a worker; remove it before launching a worker there.");
        }
    }
}
