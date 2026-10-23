package io.github.ythibos.edi.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record PrimaryLaunch(String session, Path directory, Path settings, Path systemPrompt) {

    private static final String SEPARATOR = ": ";
    private static final String OPENING_PROMPT = "Hello EDI, SITREP board, please.";

    static PrimaryLaunch read(String text) {
        Map<String, String> fields = text.lines()
                .filter(line -> line.contains(SEPARATOR))
                .collect(Collectors.toMap(line -> line.substring(0, line.indexOf(SEPARATOR)), line -> line.substring(line.indexOf(SEPARATOR) + 2),
                        (first, _) -> first));
        return new PrimaryLaunch(required(fields, "session"), Path.of(required(fields, "directory")), Path.of(required(fields, "settings")),
                Path.of(required(fields, "system prompt")));
    }

    List<String> claude() {
        return List.of("claude", "--settings", settings.toString(), "--append-system-prompt-file", systemPrompt.toString(), "--", OPENING_PROMPT);
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new CommandFailure("EDI core did not say the primary session's " + name + ".");
        }
        return value.strip();
    }
}
