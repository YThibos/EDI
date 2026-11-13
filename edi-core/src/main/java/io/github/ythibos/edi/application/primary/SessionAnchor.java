package io.github.ythibos.edi.application.primary;

import java.nio.file.Path;
import java.util.Optional;

import io.github.ythibos.edi.domain.primary.HeldAnchor;

public record SessionAnchor(HeldAnchor held, String heading, Path home, Path outputStyle, Optional<Path> currentAnchor) {
}
