package io.github.ythibos.edi.domain.delivery;

import java.util.List;
import java.util.Objects;

public record LocalChange(List<ChangedPath> paths, String patch) {

    public LocalChange {
        paths = List.copyOf(paths);
        Objects.requireNonNull(patch, "A local change carries the patch it applies.");
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public List<String> files() {
        return paths.stream().flatMap(ChangedPath::touchedFiles).distinct().toList();
    }

    public List<ChangedPath> heldForTheCaptain() {
        return paths.stream().filter(ChangedPath::needsTheCaptainsEye).toList();
    }
}
