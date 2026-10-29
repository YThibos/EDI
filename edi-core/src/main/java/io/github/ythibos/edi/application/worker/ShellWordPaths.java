package io.github.ythibos.edi.application.worker;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.github.ythibos.edi.domain.guard.BraceExpansion;
import io.github.ythibos.edi.domain.guard.PathLookup;
import io.github.ythibos.edi.domain.guard.ShellGlob;
import io.github.ythibos.edi.domain.guard.UntraceablePaths;

final class ShellWordPaths implements PathLookup {

    static final int MOST_PATHS = 1024;

    private static final String SEPARATOR = "/";
    private static final String ANY_DEPTH = "**";
    private static final String HIDDEN = ".";
    private static final List<String> DIRECTORY_ENTRIES = List.of(".", "..");

    private final Path workingDirectory;
    private final Map<String, Path> homes = new LinkedHashMap<>();

    ShellWordPaths(Path workingDirectory, Path userHome, String userName, Path ediHome) {
        this.workingDirectory = workingDirectory;
        homes.put("~", userHome);
        homes.put("~" + userName, userHome);
        homes.put("$HOME", userHome);
        homes.put("${HOME}", userHome);
        homes.put("$EDI_HOME", ediHome);
        homes.put("${EDI_HOME}", ediHome);
        if (workingDirectory != null) {
            homes.put("~+", workingDirectory);
        }
    }

    @Override
    public List<String> pathsOf(String pattern) {
        return BraceExpansion.of(pattern, MOST_PATHS).stream()
                .map(this::expanded)
                .flatMap(word -> absolute(word).stream())
                .flatMap(word -> matchesOf(word).stream())
                .flatMap(path -> Stream.of(path.normalize(), RealPaths.of(path)))
                .map(Path::toString)
                .distinct()
                .toList();
    }

    private String expanded(String word) {
        for (Map.Entry<String, Path> home : homes.entrySet()) {
            if (word.equals(home.getKey()) || word.startsWith(home.getKey() + SEPARATOR)) {
                return home.getValue() + word.substring(home.getKey().length());
            }
        }
        return word;
    }

    private Optional<Path> absolute(String word) {
        try {
            Path written = Path.of(word);
            if (written.isAbsolute() || workingDirectory != null) {
                return Optional.of(written.isAbsolute() ? written : workingDirectory.resolve(written));
            }
            return Optional.empty();
        } catch (InvalidPathException unreadable) {
            return Optional.empty();
        }
    }

    private static List<Path> matchesOf(Path pattern) {
        List<Path> reached = List.of(pattern.getRoot());
        for (Path segment : pattern) {
            reached = reachedThrough(reached, segment.toString());
        }
        return reached;
    }

    private static List<Path> reachedThrough(List<Path> reached, String segment) {
        if (ANY_DEPTH.equals(segment)) {
            return everythingWithin(reached);
        }
        if (ShellGlob.isAGlob(segment)) {
            return childrenMatching(reached, segment);
        }
        String name = ShellGlob.literal(segment);
        return reached.stream().map(path -> path.resolve(name)).toList();
    }

    private static List<Path> everythingWithin(List<Path> directories) {
        Matches matches = new Matches();
        List<Path> unvisited = new ArrayList<>(directories);
        while (!unvisited.isEmpty()) {
            Path directory = unvisited.removeLast();
            matches.add(directory);
            if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                unvisited.addAll(childrenOf(directory));
            }
        }
        return matches.paths();
    }

    private static List<Path> childrenOf(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            return StreamSupport.stream(entries.spliterator(), false).toList();
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private static List<Path> childrenMatching(List<Path> directories, String segment) {
        Pattern glob = ShellGlob.of(segment);
        Matches matches = new Matches();
        for (Path directory : directories) {
            directoryEntriesMatching(segment, glob).forEach(entry -> matches.add(directory.resolve(entry)));
            childrenOf(directory).stream().filter(child -> glob.matcher(child.getFileName().toString()).matches()).forEach(matches::add);
        }
        return matches.paths();
    }

    private static List<String> directoryEntriesMatching(String segment, Pattern glob) {
        return segment.startsWith(HIDDEN) ? DIRECTORY_ENTRIES.stream().filter(entry -> glob.matcher(entry).matches()).toList() : List.of();
    }

    private static final class Matches {

        private final List<Path> paths = new ArrayList<>();

        void add(Path path) {
            if (paths.size() >= MOST_PATHS) {
                throw new UntraceablePaths("its glob reaches more than " + MOST_PATHS + " files");
            }
            paths.add(path);
        }

        List<Path> paths() {
            return paths;
        }
    }
}
