package io.github.ythibos.edi.domain.guard;

import java.util.List;

public record Secrets(List<String> fileEndings, boolean forgeTokens) {

    public static final Secrets NONE = new Secrets(List.of(), false);

    public Secrets {
        fileEndings = fileEndings.stream().filter(ending -> ending.startsWith("/") && ending.length() > 1).toList();
    }

    boolean areNamedIn(String word) {
        return fileEndings.stream().anyMatch(word::endsWith);
    }
}
