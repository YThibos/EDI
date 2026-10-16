package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.stream.Stream;

public final class ShellScript {

    private static final int DEEPEST_NESTING = 12;

    private ShellScript() {
    }

    public static List<ShellCommand> commandsIn(String script) {
        return commandsIn(script, 0);
    }

    static List<ShellCommand> commandsIn(String script, int nesting) {
        if (nesting > DEEPEST_NESTING) {
            throw new UnreadableCommand("The command nests more than " + DEEPEST_NESTING + " levels of commands inside commands.");
        }
        return ShellParser.simpleCommandsIn(script, nesting).stream()
                .flatMap(words -> CommandWrappers.unwrapped(words, nesting).stream())
                .flatMap(ShellScript::withSubstitutions)
                .toList();
    }

    private static Stream<ShellCommand> withSubstitutions(ShellCommand command) {
        return Stream.concat(Stream.of(command), command.arguments().stream().flatMap(word -> word.substitutions().stream()));
    }
}
