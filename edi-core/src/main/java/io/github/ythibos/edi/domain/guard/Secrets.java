package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record Secrets(List<String> fileEndings, boolean forgeTokens, PathLookup paths) {

    public static final Secrets NONE = new Secrets(List.of(), false);

    private static final String FILE_READ_MARK = "@";
    private static final String QUOTED_FILE_READ_MARK = "\\@";
    private static final Pattern SHORT_OPTIONS = Pattern.compile("-[A-Za-z]+");

    public Secrets {
        fileEndings = fileEndings.stream().filter(ending -> ending.startsWith("/") && ending.length() > 1).toList();
        Objects.requireNonNull(paths, "The guard knows how to find the paths a shell word leads to.");
    }

    public Secrets(List<String> fileEndings, boolean forgeTokens) {
        this(fileEndings, forgeTokens, PathLookup.AS_WRITTEN);
    }

    boolean areNamedIn(String file) {
        return !fileEndings.isEmpty() && endsInASecret(file);
    }

    boolean areNamedIn(ShellWord word) {
        return !fileEndings.isEmpty() && (endsInASecret(word.text()) || leadsToASecret(word.pattern()));
    }

    private boolean endsInASecret(String path) {
        return fileEndings.stream().anyMatch(path::endsWith);
    }

    private boolean leadsToASecret(String pattern) {
        return pathsNamedIn(pattern).flatMap(path -> paths.pathsOf(path).stream()).anyMatch(this::endsInASecret);
    }

    private static Stream<String> pathsNamedIn(String pattern) {
        return Stream.concat(Stream.of(pattern.startsWith("-") ? "" : pattern, valueAfterTheFirstEquals(pattern)), valuesAfterShortOptions(pattern))
                .flatMap(path -> Stream.of(path, withoutAFileReadMark(path)))
                .filter(path -> !path.isBlank())
                .distinct();
    }

    private static String valueAfterTheFirstEquals(String pattern) {
        int equals = pattern.indexOf('=');
        return equals > 0 ? pattern.substring(equals + 1) : "";
    }

    private static Stream<String> valuesAfterShortOptions(String pattern) {
        Matcher letters = SHORT_OPTIONS.matcher(pattern);
        if (!letters.lookingAt() || pattern.startsWith("--")) {
            return Stream.empty();
        }
        return IntStream.rangeClosed(2, letters.end()).mapToObj(pattern::substring);
    }

    private static String withoutAFileReadMark(String path) {
        if (path.startsWith(QUOTED_FILE_READ_MARK)) {
            return path.substring(QUOTED_FILE_READ_MARK.length());
        }
        return path.startsWith(FILE_READ_MARK) ? path.substring(FILE_READ_MARK.length()) : path;
    }
}
