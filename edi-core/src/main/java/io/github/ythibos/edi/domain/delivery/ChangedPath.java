package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;
import java.util.stream.Stream;

public record ChangedPath(String path, PathChange change, String formerPath) {

    public ChangedPath {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("A changed path names the file it changes.");
        }
        Objects.requireNonNull(change, () -> path + " is added, modified, deleted, renamed or has its mode changed.");
        if ((change == PathChange.RENAMED) != (formerPath != null)) {
            throw new IllegalArgumentException(path + " names the path it was renamed from exactly when it is renamed.");
        }
    }

    public static ChangedPath of(String path, PathChange change) {
        return new ChangedPath(path, change, null);
    }

    public static ChangedPath renamed(String formerPath, String path) {
        return new ChangedPath(path, PathChange.RENAMED, formerPath);
    }

    public Stream<String> touchedFiles() {
        return formerPath == null ? Stream.of(path) : Stream.of(formerPath, path);
    }

    public boolean needsTheCaptainsEye() {
        return change.isDestructive();
    }

    @Override
    public String toString() {
        return formerPath == null ? change.word() + " " + path : change.word() + " " + formerPath + " to " + path;
    }
}
