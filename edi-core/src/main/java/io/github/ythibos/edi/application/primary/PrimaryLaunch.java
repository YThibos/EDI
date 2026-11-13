package io.github.ythibos.edi.application.primary;

import java.nio.file.Path;

public record PrimaryLaunch(Path directory, Path settings, String sessionName) {
}
