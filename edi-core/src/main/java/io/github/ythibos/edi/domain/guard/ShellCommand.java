package io.github.ythibos.edi.domain.guard;

import java.util.List;

public record ShellCommand(String programAsWritten, List<ShellWord> arguments, boolean argumentsFromInput, List<String> assignedVariables) {

    private static final String NO_PROGRAM = "";
    private static final char PATH_SEPARATOR = '/';

    public ShellCommand {
        arguments = List.copyOf(arguments);
        assignedVariables = List.copyOf(assignedVariables);
    }

    static ShellCommand withoutAProgram(List<ShellWord> redirections, List<String> assignedVariables) {
        return new ShellCommand(NO_PROGRAM, redirections, false, assignedVariables);
    }

    public String program() {
        return programAsWritten.substring(programAsWritten.lastIndexOf(PATH_SEPARATOR) + 1);
    }

    public boolean runs(String name) {
        return program().equals(name);
    }

    public List<String> argumentTexts() {
        return arguments.stream().filter(word -> !word.redirectionTarget()).map(ShellWord::text).toList();
    }

    public List<String> filesWritten() {
        return arguments.stream().filter(ShellWord::writtenTo).map(ShellWord::text).toList();
    }
}
