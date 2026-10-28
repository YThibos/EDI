package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Secrets(List<String> fileEndings, boolean forgeTokens, PathLookup paths) {

    public static final Secrets NONE = new Secrets(List.of(), false);

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
        return pathIn(pattern).stream().flatMap(path -> paths.pathsOf(path).stream()).anyMatch(this::endsInASecret);
    }

    private static Optional<String> pathIn(String pattern) {
        if (!pattern.startsWith("-")) {
            return pattern.isBlank() ? Optional.empty() : Optional.of(pattern);
        }
        int equals = pattern.indexOf('=');
        return equals > 0 ? Optional.of(pattern.substring(equals + 1)) : Optional.empty();
    }
}
