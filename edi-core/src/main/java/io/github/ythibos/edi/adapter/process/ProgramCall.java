package io.github.ythibos.edi.adapter.process;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ProgramCall(
        Program program,
        String executable,
        Path directory,
        List<String> arguments,
        Map<String, String> environment,
        Set<String> withheldVariables,
        String input) {

    public ProgramCall {
        Objects.requireNonNull(program, "A call runs a known program.");
        executable = executable == null || executable.isBlank() ? program.executable() : executable;
        Objects.requireNonNull(directory, () -> program.executable() + " runs in a directory.");
        arguments = List.copyOf(arguments);
        environment = Map.copyOf(environment);
        withheldVariables = Set.copyOf(withheldVariables);
    }

    public static ProgramCall of(Program program, String executable, Path directory, List<String> arguments) {
        return new ProgramCall(program, executable, directory, arguments, Map.of(), Set.of(), null);
    }

    public ProgramCall withVariables(Map<String, String> variables) {
        Map<String, String> more = new HashMap<>(environment);
        more.putAll(variables);
        return new ProgramCall(program, executable, directory, arguments, more, withheldVariables, input);
    }

    public ProgramCall withholding(Set<String> variables) {
        Set<String> withheld = Stream.concat(withheldVariables.stream(), variables.stream()).collect(Collectors.toSet());
        return new ProgramCall(program, executable, directory, arguments, environment, withheld, input);
    }

    public ProgramCall withInput(String text) {
        return new ProgramCall(program, executable, directory, arguments, environment, withheldVariables, text);
    }
}
