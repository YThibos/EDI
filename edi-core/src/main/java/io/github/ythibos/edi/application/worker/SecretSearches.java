package io.github.ythibos.edi.application.worker;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

final class SecretSearches {

    private static final String ANY_DEPTH = "**/";

    private SecretSearches() {
    }

    static boolean reach(Path directory, String pattern, Path secret) {
        List<Path> secretPaths = List.of(secret.toAbsolutePath().normalize(), ShellWordPaths.realPathOf(secret));
        if (Path.of(pattern).isAbsolute()) {
            return secretPaths.stream().anyMatch(path -> matches(pattern, path));
        }
        return List.of(directory.toAbsolutePath().normalize(), ShellWordPaths.realPathOf(directory)).stream()
                .anyMatch(base -> secretPaths.stream().filter(path -> path.startsWith(base)).anyMatch(path -> matchesBelow(pattern, base.relativize(path))));
    }

    private static boolean matches(String pattern, Path path) {
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(path);
        } catch (IllegalArgumentException unreadable) {
            return false;
        }
    }

    private static boolean matchesBelow(String pattern, Path relative) {
        return matches(pattern, relative) || pattern.startsWith(ANY_DEPTH) && matches(pattern.substring(ANY_DEPTH.length()), relative);
    }
}
