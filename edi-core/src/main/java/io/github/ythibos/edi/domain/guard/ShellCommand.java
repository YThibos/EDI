package io.github.ythibos.edi.domain.guard;

import java.util.List;

public record ShellCommand(String program, List<ShellWord> arguments, boolean argumentsFromInput, List<String> assignedVariables) {

    private static final String NO_PROGRAM = "";

    public ShellCommand {
        arguments = List.copyOf(arguments);
        assignedVariables = List.copyOf(assignedVariables);
    }

    static ShellCommand assigningOnly(List<String> assignedVariables) {
        return new ShellCommand(NO_PROGRAM, List.of(), false, assignedVariables);
    }

    public boolean runs(String name) {
        return program.equals(name);
    }

    public List<String> argumentTexts() {
        return arguments.stream().map(ShellWord::text).toList();
    }
}
