package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;
import java.util.Optional;

public record BuildFile(String path, MavenCoordinates own, Optional<ParentReference> parent) {

    public BuildFile {
        Objects.requireNonNull(parent, "A build file says whether it names a parent.");
    }
}
