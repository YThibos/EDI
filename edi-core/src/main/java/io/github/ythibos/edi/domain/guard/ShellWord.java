package io.github.ythibos.edi.domain.guard;

import java.util.List;

public record ShellWord(String text, List<ShellCommand> substitutions, boolean redirectionTarget) {

    public ShellWord {
        substitutions = List.copyOf(substitutions);
    }

    public boolean isSubstituted() {
        return !substitutions.isEmpty();
    }
}
