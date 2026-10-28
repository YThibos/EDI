package io.github.ythibos.edi.application.worker;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.github.ythibos.edi.domain.guard.PathLookup;

final class ShellWordPaths implements PathLookup {

    private static final String GLOB_CHARACTERS = "*?[{";
    private static final int MOST_MATCHES = 256;
    private static final String SEPARATOR = "/";

    private final Path workingDirectory;
    private final Map<String, String> variables;

    ShellWordPaths(Path workingDirectory, Path userHome, Path ediHome) {
        this.workingDirectory = workingDirectory;
        this.variables = Map.of("~", userHome.toString(), "$HOME", userHome.toString(), "${HOME}", userHome.toString(),
                "$EDI_HOME", ediHome.toString(), "${EDI_HOME}", ediHome.toString());
    }

    @Override
    public List<String> pathsOf(String word) {
        try {
            Path written = Path.of(expanded(word));
            if (!written.isAbsolute() && workingDirectory == null) {
                return List.of();
            }
            Path absolute = written.isAbsolute() ? written : workingDirectory.resolve(written);
            return (isAGlob(absolute.toString()) ? matchesOf(absolute) : List.of(absolute)).stream()
                    .flatMap(path -> Stream.of(path.normalize(), realPathOf(path)))
                    .map(Path::toString)
                    .distinct()
                    .toList();
        } catch (InvalidPathException unreadable) {
            return List.of();
        }
    }

    private String expanded(String word) {
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            if (word.equals(variable.getKey()) || word.startsWith(variable.getKey() + SEPARATOR)) {
                return variable.getValue() + word.substring(variable.getKey().length());
            }
        }
        return word;
    }

    private static boolean isAGlob(String path) {
        return path.chars().anyMatch(character -> GLOB_CHARACTERS.indexOf(character) >= 0);
    }

    private static List<Path> matchesOf(Path pattern) {
        List<Path> reached = List.of(pattern.getRoot());
        for (Path segment : pattern) {
            reached = isAGlob(segment.toString())
                    ? childrenMatching(reached, segment.toString())
                    : reached.stream().map(path -> path.resolve(segment)).toList();
        }
        return reached;
    }

    private static List<Path> childrenMatching(List<Path> directories, String glob) {
        List<Path> matches = new ArrayList<>();
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            for (Path directory : directories) {
                matches.addAll(childrenOf(directory, matcher, MOST_MATCHES - matches.size()));
            }
        } catch (IllegalArgumentException unreadable) {
            return List.of();
        }
        return matches;
    }

    private static List<Path> childrenOf(Path directory, PathMatcher matcher, int room) {
        List<Path> children = new ArrayList<>();
        if (room <= 0 || !Files.isDirectory(directory)) {
            return children;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory, entry -> matcher.matches(entry.getFileName()))) {
            for (Path entry : entries) {
                if (children.size() >= room) {
                    break;
                }
                children.add(entry);
            }
        } catch (IOException unreadable) {
            return children;
        }
        return children;
    }

    static Path realPathOf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException absent) {
            return realisedParentOf(path.toAbsolutePath().normalize());
        }
    }

    private static Path realisedParentOf(Path absolute) {
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute));
        } catch (IOException unreadable) {
            return absolute;
        }
    }
}
