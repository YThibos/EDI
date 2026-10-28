package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class SettingsWrites {

    private static final Set<String> FILE_WRITERS = Set.of("tee", "touch", "truncate");
    private static final Set<String> FILE_COPIERS = Set.of("cp", "mv", "install", "ln", "rsync");
    private static final Set<String> TARGET_DIRECTORY = Set.of("-t", "--target-directory");
    private static final String TARGET_DIRECTORY_GIVEN = "--target-directory=";
    private static final String OUTPUT_FILE = "of=";

    private SettingsWrites() {
    }

    public static Optional<GuardRefusal> refusalForWriting(String file, Predicate<String> underTheSettings) {
        return underTheSettings.test(file) ? refused() : Optional.empty();
    }

    public static Optional<GuardRefusal> refusalFor(String script, Predicate<String> underTheSettings) {
        try {
            return ShellScript.commandsIn(script).stream().flatMap(command -> targetsOf(command).stream()).anyMatch(underTheSettings)
                    ? refused()
                    : Optional.empty();
        } catch (UnreadableCommand unreadable) {
            return Optional.empty();
        }
    }

    private static List<String> targetsOf(ShellCommand command) {
        List<String> targets = new ArrayList<>(List.of(command.program()));
        command.arguments().stream().filter(ShellWord::redirectionTarget).map(ShellWord::text).forEach(targets::add);
        if (FILE_WRITERS.contains(command.program()) || command.runs("sed") && editsInPlace(command.argumentTexts())) {
            targets.addAll(operands(command.argumentTexts()));
        }
        if (FILE_COPIERS.contains(command.program())) {
            targets.addAll(destinationsOf(command.argumentTexts()));
        }
        if (command.runs("dd")) {
            command.argumentTexts().stream().filter(argument -> argument.startsWith(OUTPUT_FILE)).map(argument -> argument.substring(OUTPUT_FILE.length()))
                    .forEach(targets::add);
        }
        return targets;
    }

    private static boolean editsInPlace(List<String> arguments) {
        return arguments.stream().anyMatch(argument -> argument.startsWith("--in-place") || argument.startsWith("-i")
                || argument.startsWith("-") && !argument.startsWith("--") && argument.indexOf('i') > 0);
    }

    private static List<String> operands(List<String> arguments) {
        return arguments.stream().filter(argument -> !argument.startsWith("-")).toList();
    }

    private static List<String> destinationsOf(List<String> arguments) {
        List<String> destinations = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (TARGET_DIRECTORY.contains(argument) && index + 1 < arguments.size()) {
                destinations.add(arguments.get(index + 1));
            } else if (argument.startsWith(TARGET_DIRECTORY_GIVEN)) {
                destinations.add(argument.substring(TARGET_DIRECTORY_GIVEN.length()));
            }
        }
        List<String> operands = operands(arguments);
        if (!operands.isEmpty()) {
            destinations.add(operands.getLast());
        }
        return destinations;
    }

    private static Optional<GuardRefusal> refused() {
        return Optional.of(new GuardRefusal(GuardRule.CLAUDE_SETTINGS, "a worker never writes under its copy's .claude directory, where a Claude Code"
                + " settings file could switch off the hooks that guard it; the captain changes those files (I-07)."));
    }
}
