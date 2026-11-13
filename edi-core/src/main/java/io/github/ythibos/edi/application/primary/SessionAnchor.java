package io.github.ythibos.edi.application.primary;

import java.nio.file.Path;

public record SessionAnchor(boolean loaded, String heading, Path home, Path outputStyle) {
}
