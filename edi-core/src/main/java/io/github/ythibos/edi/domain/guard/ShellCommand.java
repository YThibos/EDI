package io.github.ythibos.edi.domain.guard;

import java.util.List;

public record ShellCommand(String program, List<ShellWord> arguments, boolean argumentsFromInput) {

    public ShellCommand {
        arguments = List.copyOf(arguments);
    }

    public boolean runs(String name) {
        return program.equals(name);
    }

    public List<String> argumentTexts() {
        return arguments.stream().map(ShellWord::text).toList();
    }
}
