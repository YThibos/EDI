package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.github.ythibos.edi.domain.rule.Rule;

public final class SettingsWrites {

    private static final Set<String> FILE_WRITERS = Set.of("tee", "touch", "truncate");
    private static final Set<String> IN_PLACE_EDITORS = Set.of("sed", "perl");
    private static final Set<String> FILE_COPIERS = Set.of("cp", "mv", "install", "ln", "rsync");
    private static final Set<String> CHECKOUTS = Set.of("checkout", "restore");
    private static final Set<String> TARGET_DIRECTORY = Set.of("-t", "--target-directory");
    private static final Set<String> GIT_OPTIONS_WITH_VALUES = Set.of("-C", "-c", "--git-dir", "--work-tree", "--namespace");
    private static final Set<String> CHECKOUT_OPTIONS_WITH_VALUES = Set.of("-b", "-B", "--orphan", "-s", "--source", "--conflict");
    private static final Set<String> TAR_EXTRACTIONS = Set.of("--extract", "--get");
    private static final Set<String> TAR_DIRECTORY = Set.of("-C", "--directory");
    private static final String TARGET_DIRECTORY_GIVEN = "--target-directory=";
    private static final String TAR_DIRECTORY_GIVEN = "--directory=";
    private static final String OUTPUT_FILE = "of=";
    private static final String SETTINGS_DIRECTORY = ".claude";
    private static final String WORKING_DIRECTORY = "$PWD";
    private static final String WORKING_DIRECTORY_IN_BRACES = "${PWD}";
    private static final String CURRENT_DIRECTORY = ".";
    private static final String EXPANSION = "$";

    private SettingsWrites() {
    }

    public static Optional<GuardRefusal> refusalForWriting(String file, Predicate<String> underTheSettings) {
        return underTheSettings.test(file) ? refused() : Optional.empty();
    }

    public static Optional<GuardRefusal> refusalFor(String script, Predicate<String> underTheSettings) {
        try {
            return ShellScript.commandsIn(script).stream().anyMatch(command -> writesTheSettings(command, underTheSettings)) ? refused() : Optional.empty();
        } catch (UnreadableCommand unreadable) {
            return Optional.empty();
        }
    }

    private static boolean writesTheSettings(ShellCommand command, Predicate<String> underTheSettings) {
        return underTheSettings.test(command.program()) || targetsOf(command).stream().map(SettingsWrites::withTheWorkingDirectoryRead)
                .anyMatch(target -> target.contains(EXPANSION) || underTheSettings.test(target));
    }

    private static List<String> targetsOf(ShellCommand command) {
        List<String> targets = new ArrayList<>();
        command.arguments().stream().filter(ShellWord::redirectionTarget).map(ShellWord::text).forEach(targets::add);
        List<String> arguments = command.argumentTexts();
        if (FILE_WRITERS.contains(command.program()) || IN_PLACE_EDITORS.contains(command.program()) && editsInPlace(arguments)) {
            targets.addAll(operands(arguments));
        }
        if (FILE_COPIERS.contains(command.program())) {
            targets.addAll(destinationsOf(command.program(), arguments));
        }
        if (command.runs("mv")) {
            operands(arguments).stream().filter(SettingsWrites::namesTheSettingsDirectory).forEach(targets::add);
        }
        if (command.runs("dd")) {
            arguments.stream().filter(argument -> argument.startsWith(OUTPUT_FILE)).map(argument -> argument.substring(OUTPUT_FILE.length()))
                    .forEach(targets::add);
        }
        if (command.runs("git")) {
            targets.addAll(checkedOutPaths(arguments));
        }
        if (command.runs("tar") && extracts(arguments)) {
            targets.add(inside(extractionDirectoryOf(arguments), SETTINGS_DIRECTORY));
        }
        return targets;
    }

    private static String withTheWorkingDirectoryRead(String word) {
        for (String form : List.of(WORKING_DIRECTORY_IN_BRACES, WORKING_DIRECTORY)) {
            if (word.equals(form) || word.startsWith(form + "/")) {
                return CURRENT_DIRECTORY + word.substring(form.length());
            }
        }
        return word;
    }

    private static boolean editsInPlace(List<String> arguments) {
        return arguments.stream().anyMatch(argument -> argument.startsWith("--in-place") || argument.startsWith("-i")
                || argument.startsWith("-") && !argument.startsWith("--") && argument.indexOf('i') > 0);
    }

    private static List<String> operands(List<String> arguments) {
        return arguments.stream().filter(argument -> !argument.startsWith("-")).toList();
    }

    private static List<String> destinationsOf(String copier, List<String> arguments) {
        List<String> directories = new ArrayList<>();
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (TARGET_DIRECTORY.contains(argument) && index + 1 < arguments.size()) {
                directories.add(arguments.get(index + 1));
            } else if (argument.startsWith(TARGET_DIRECTORY_GIVEN)) {
                directories.add(argument.substring(TARGET_DIRECTORY_GIVEN.length()));
            }
        }
        List<String> operands = operands(arguments).stream().filter(operand -> !directories.contains(operand)).toList();
        if (directories.isEmpty() && !operands.isEmpty()) {
            directories.add(operands.getLast());
            operands = operands.subList(0, operands.size() - 1);
        }
        List<String> destinations = new ArrayList<>(directories);
        for (String directory : directories) {
            operands.forEach(source -> destinations.add(inside(directory, copied(copier, source))));
        }
        return destinations;
    }

    private static String copied(String copier, String source) {
        boolean copiesItsContents = source.endsWith("/.") || copier.equals("rsync") && source.endsWith("/");
        return copiesItsContents ? SETTINGS_DIRECTORY : lastSegmentOf(source);
    }

    private static boolean namesTheSettingsDirectory(String operand) {
        return lastSegmentOf(operand).equals(SETTINGS_DIRECTORY);
    }

    private static List<String> checkedOutPaths(List<String> arguments) {
        int index = 0;
        String directory = null;
        while (index < arguments.size() && arguments.get(index).startsWith("-")) {
            if (arguments.get(index).equals("-C") && index + 1 < arguments.size()) {
                directory = arguments.get(index + 1);
            }
            index += GIT_OPTIONS_WITH_VALUES.contains(arguments.get(index)) ? 2 : 1;
        }
        if (index >= arguments.size() || !CHECKOUTS.contains(arguments.get(index))) {
            return List.of();
        }
        String from = directory;
        return pathsAfter(arguments.subList(index + 1, arguments.size())).stream().map(path -> from == null ? path : inside(from, path)).toList();
    }

    private static List<String> pathsAfter(List<String> arguments) {
        List<String> paths = new ArrayList<>();
        int index = 0;
        while (index < arguments.size()) {
            String argument = arguments.get(index);
            if (CHECKOUT_OPTIONS_WITH_VALUES.contains(argument)) {
                index += 2;
                continue;
            }
            if (!argument.startsWith("-")) {
                paths.add(argument);
            }
            index++;
        }
        return paths;
    }

    private static boolean extracts(List<String> arguments) {
        return arguments.stream().anyMatch(argument -> TAR_EXTRACTIONS.contains(argument)
                || argument.startsWith("-") && !argument.startsWith("--") && argument.indexOf('x') > 0)
                || !arguments.isEmpty() && !arguments.getFirst().startsWith("-") && arguments.getFirst().indexOf('x') >= 0;
    }

    private static String extractionDirectoryOf(List<String> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (TAR_DIRECTORY.contains(argument) && index + 1 < arguments.size()) {
                return arguments.get(index + 1);
            }
            if (argument.startsWith(TAR_DIRECTORY_GIVEN)) {
                return argument.substring(TAR_DIRECTORY_GIVEN.length());
            }
        }
        return CURRENT_DIRECTORY;
    }

    private static String inside(String directory, String name) {
        return (directory.endsWith("/") ? directory : directory + "/") + name;
    }

    private static String lastSegmentOf(String path) {
        String trimmed = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    private static Optional<GuardRefusal> refused() {
        return Optional.of(new GuardRefusal(Rule.CLAUDE_SETTINGS, "a worker never writes under its copy's .claude directory, where a Claude Code"
                + " settings file could switch off the hooks that guard it; the captain changes those files (I-07)."));
    }
}
