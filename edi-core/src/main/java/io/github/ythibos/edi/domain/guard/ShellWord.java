package io.github.ythibos.edi.domain.guard;

import java.util.List;

public record ShellWord(String text, String pattern, List<ShellCommand> substitutions, Redirection redirection) {

    public ShellWord {
        substitutions = List.copyOf(substitutions);
    }

    public boolean isSubstituted() {
        return !substitutions.isEmpty();
    }

    public boolean redirectionTarget() {
        return redirection.redirects();
    }

    public boolean writtenTo() {
        return redirection.writesAFile();
    }
}
