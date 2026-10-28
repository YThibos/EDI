package io.github.ythibos.edi.application.worker;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import io.github.ythibos.edi.domain.guard.BraceExpansion;
import io.github.ythibos.edi.domain.guard.ShellGlob;
import io.github.ythibos.edi.domain.guard.UntraceablePaths;

final class SecretSearches {

    private static final String ANY_DEPTH = "**";
    private static final String HERE = ".";
    private static final String UP = "..";

    private SecretSearches() {
    }

    static boolean reach(Path directory, String pattern, Path secret) {
        List<Path> secretPaths = List.of(secret.toAbsolutePath().normalize(), ShellWordPaths.realPathOf(secret));
        return BraceExpansion.of(pattern, ShellWordPaths.MOST_PATHS).stream()
                .flatMap(alternative -> searchesFor(directory, alternative).stream())
                .anyMatch(search -> secretPaths.stream().anyMatch(search::reaches));
    }

    private static List<Search> searchesFor(Path directory, String pattern) {
        Path rooted = rooted(directory, pattern);
        Search asWritten = Search.of(rooted);
        return List.of(Search.of(rooted.normalize()), new Search(ShellWordPaths.realPathOf(asWritten.base()), asWritten.rest()));
    }

    private static Path rooted(Path directory, String pattern) {
        try {
            return directory.toAbsolutePath().resolve(pattern);
        } catch (InvalidPathException unreadable) {
            throw new UntraceablePaths("its pattern is not a path");
        }
    }

    private record Search(Path base, List<String> rest) {

        static Search of(Path rooted) {
            List<String> names = StreamSupport.stream(rooted.spliterator(), false).map(Path::toString).toList();
            int firstGlob = IntStream.range(0, names.size()).filter(at -> ShellGlob.isAGlob(names.get(at))).findFirst().orElse(names.size());
            Path base = rooted.getRoot();
            for (String name : names.subList(0, firstGlob)) {
                base = base.resolve(ShellGlob.literal(name));
            }
            return new Search(base, normalised(names.subList(firstGlob, names.size())));
        }

        boolean reaches(Path secret) {
            return secret.startsWith(base) && matches(rest, namesOf(base.relativize(secret)));
        }

        private static List<String> normalised(List<String> names) {
            List<String> normalised = new ArrayList<>();
            for (String name : names) {
                if (UP.equals(name) && !normalised.isEmpty() && !UP.equals(normalised.getLast())) {
                    normalised.removeLast();
                } else if (!HERE.equals(name)) {
                    normalised.add(name);
                }
            }
            return normalised;
        }

        private static List<String> namesOf(Path relative) {
            return relative.toString().isEmpty() ? List.of() : StreamSupport.stream(relative.spliterator(), false).map(Path::toString).toList();
        }

        private static boolean matches(List<String> patterns, List<String> names) {
            if (patterns.isEmpty()) {
                return names.isEmpty();
            }
            List<String> laterPatterns = patterns.subList(1, patterns.size());
            if (ANY_DEPTH.equals(patterns.getFirst())) {
                return IntStream.rangeClosed(0, names.size()).anyMatch(skipped -> matches(laterPatterns, names.subList(skipped, names.size())));
            }
            return !names.isEmpty() && ShellGlob.of(patterns.getFirst()).matcher(names.getFirst()).matches()
                    && matches(laterPatterns, names.subList(1, names.size()));
        }
    }
}
