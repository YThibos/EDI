package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

record PrimaryLaunch(String session, Path directory, Path settings, Path systemPrompt, String launchKey, UUID resumedSession) {

    private static final String SEPARATOR = ": ";

    static PrimaryLaunch read(String text) {
        Map<String, String> fields = text.lines()
                .filter(line -> line.contains(SEPARATOR))
                .collect(Collectors.toMap(line -> line.substring(0, line.indexOf(SEPARATOR)), line -> line.substring(line.indexOf(SEPARATOR) + 2),
                        (first, _) -> first));
        return new PrimaryLaunch(required(fields, "session"), Path.of(required(fields, "directory")), Path.of(required(fields, "settings")),
                Path.of(required(fields, "system prompt")), required(fields, "launch key"), null);
    }

    PrimaryLaunch resuming(UUID claudeSession) {
        return new PrimaryLaunch(session, directory, settings, systemPrompt, launchKey, claudeSession);
    }

    String launchKeyVariable() {
        return ClaudeSession.PRIMARY_LAUNCH_KEY + "=" + launchKey;
    }

    List<String> claude() {
        List<String> command = new ArrayList<>(List.of("claude", "--settings", settings.toString(), "--append-system-prompt-file", systemPrompt.toString()));
        if (resumedSession != null) {
            command.addAll(List.of("--resume", resumedSession.toString()));
        }
        return command;
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new CommandFailure("EDI core did not say the primary session's " + name + ".");
        }
        return value.strip();
    }
}
