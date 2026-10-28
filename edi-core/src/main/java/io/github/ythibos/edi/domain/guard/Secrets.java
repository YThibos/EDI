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

    boolean areNamedIn(String word) {
        return !fileEndings.isEmpty() && (endsInASecret(word) || leadsToASecret(word));
    }

    private boolean leadsToASecret(String word) {
        return pathIn(word).stream().flatMap(path -> paths.pathsOf(path).stream()).anyMatch(this::endsInASecret);
    }

    private boolean endsInASecret(String path) {
        return fileEndings.stream().anyMatch(path::endsWith);
    }

    private static Optional<String> pathIn(String word) {
        if (!word.startsWith("-")) {
            return word.isBlank() ? Optional.empty() : Optional.of(word);
        }
        int equals = word.indexOf('=');
        return equals > 0 ? Optional.of(word.substring(equals + 1)) : Optional.empty();
    }
}
