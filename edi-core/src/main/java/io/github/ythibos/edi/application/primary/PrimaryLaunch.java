package io.github.ythibos.edi.application.primary;

import java.nio.file.Path;

public record PrimaryLaunch(Path directory, Path settings, Path systemPrompt, String sessionName) {
}
