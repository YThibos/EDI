package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class CommandWrappers {

    private static final Set<String> KEYWORDS = Set.of(
            "if", "then", "else", "elif", "fi", "do", "done", "while", "until", "for", "select",
            "case", "esac", "function", "coproc", "!", "{", "}", "[[", "]]");
    private static final Set<String> SHELLS = Set.of("sh", "bash", "zsh", "dash", "ksh", "ash", "mksh");
    private static final Set<String> SHELL_VALUES = Set.of("-o", "+o", "-O", "+O", "--rcfile", "--init-file");
    private static final Set<String> PLAIN_WRAPPERS = Set.of("nohup", "builtin", "unbuffer", "chronic", "setsid", "exec");
    private static final Set<String> SUDO_VALUES = Set.of("-u", "-g", "-h", "-p", "-C", "-D", "-r", "-t", "-U", "-T", "-R");
    private static final Set<String> ENV_VALUES = Set.of("-u", "-C", "--unset", "--chdir");
    private static final Set<String> TIMEOUT_VALUES = Set.of("-s", "-k", "--signal", "--kill-after");
    private static final Set<String> NICE_VALUES = Set.of("-n", "--adjustment");
    private static final Set<String> IONICE_VALUES = Set.of("-c", "-n", "--class", "--classdata");
    private static final Set<String> STDBUF_VALUES = Set.of("-i", "-o", "-e");
    private static final Set<String> TIME_VALUES = Set.of("-f", "-o", "--format", "--output");
    private static final Set<String> XARGS_VALUES = Set.of("-I", "-L", "-n", "-P", "-s", "-d", "-E", "-a",
            "--replace", "--max-lines", "--max-args", "--max-procs", "--max-chars", "--delimiter", "--eof", "--arg-file");
    private static final Set<String> WATCH_VALUES = Set.of("-n", "-q", "--interval");
    private static final Set<String> EXECUTING_FIND_ACTIONS = Set.of("-exec", "-execdir", "-ok", "-okdir");
    private static final Set<String> FIND_ACTION_ENDS = Set.of(";", "+");
    private static final Set<String> COMMAND_LOOKUPS = Set.of("-v", "-V");

    private CommandWrappers() {
    }

    static List<ShellCommand> unwrapped(List<ShellWord> words, int nesting) {
        return unwrap(words, false, nesting);
    }

    private static List<ShellCommand> unwrap(List<ShellWord> words, boolean argumentsFromInput, int nesting) {
        int start = firstCommandWord(words);
        List<ShellCommand> droppedSubstitutions = words.subList(0, start).stream().flatMap(word -> word.substitutions().stream()).toList();
        if (start == words.size()) {
            return droppedSubstitutions;
        }
        ShellCommand command = new ShellCommand(programName(words.get(start).text()), words.subList(start + 1, words.size()), argumentsFromInput);
        return Stream.concat(droppedSubstitutions.stream(), withWhatItRuns(command, nesting).stream()).toList();
    }

    private static List<ShellCommand> withWhatItRuns(ShellCommand command, int nesting) {
        List<ShellWord> arguments = command.arguments();
        List<ShellCommand> run = new ArrayList<>(List.of(command));
        switch (command.program()) {
            case "sudo", "doas" -> run.addAll(unwrap(afterOptions(arguments, SUDO_VALUES), false, nesting));
            case "env" -> run.addAll(unwrap(withoutAssignments(afterOptions(arguments, ENV_VALUES)), false, nesting));
            case "timeout" -> run.addAll(unwrap(afterFirst(afterOptions(arguments, TIMEOUT_VALUES)), false, nesting));
            case "nice" -> run.addAll(unwrap(afterOptions(arguments, NICE_VALUES), false, nesting));
            case "ionice" -> run.addAll(unwrap(afterOptions(arguments, IONICE_VALUES), false, nesting));
            case "stdbuf" -> run.addAll(unwrap(afterOptions(arguments, STDBUF_VALUES), false, nesting));
            case "time" -> run.addAll(unwrap(afterOptions(arguments, TIME_VALUES), false, nesting));
            case "command" -> run.addAll(commandBuiltin(arguments, nesting));
            case "xargs" -> run.addAll(unwrap(afterOptions(arguments, XARGS_VALUES), true, nesting));
            case "watch" -> run.addAll(ShellScript.commandsIn(joined(afterOptions(arguments, WATCH_VALUES)), nesting + 1));
            case "eval" -> run.addAll(ShellScript.commandsIn(joined(arguments), nesting + 1));
            case "find" -> run.addAll(findActions(arguments, nesting));
            default -> run.addAll(otherWrappers(command, nesting));
        }
        return run;
    }

    private static List<ShellCommand> otherWrappers(ShellCommand command, int nesting) {
        if (PLAIN_WRAPPERS.contains(command.program())) {
            return unwrap(afterOptions(command.arguments(), Set.of("-a")), false, nesting);
        }
        if (SHELLS.contains(command.program())) {
            return shellScript(command.arguments(), nesting);
        }
        return List.of();
    }

    private static List<ShellCommand> commandBuiltin(List<ShellWord> arguments, int nesting) {
        boolean onlyLooksUp = arguments.stream().takeWhile(CommandWrappers::isOption).anyMatch(word -> COMMAND_LOOKUPS.contains(word.text()));
        return onlyLooksUp ? List.of() : unwrap(afterOptions(arguments, Set.of()), false, nesting);
    }

    private static List<ShellCommand> shellScript(List<ShellWord> words, int nesting) {
        List<ShellWord> arguments = words.stream().filter(word -> !word.redirectionTarget()).toList();
        int operand = firstShellOperand(arguments);
        List<String> options = arguments.subList(0, operand).stream().map(ShellWord::text).toList();
        if (operand == arguments.size() || namesShortFlag(options, 's')) {
            throw new UnreadableCommand("The shell reads its script from standard input, which the guard cannot see.");
        }
        return namesShortFlag(options, 'c') ? ShellScript.commandsIn(arguments.get(operand).text(), nesting + 1) : List.of();
    }

    private static int firstShellOperand(List<ShellWord> arguments) {
        int index = 0;
        while (index < arguments.size() && isShellOption(arguments.get(index).text())) {
            index += SHELL_VALUES.contains(arguments.get(index).text()) ? 2 : 1;
        }
        return Math.min(index, arguments.size());
    }

    private static boolean isShellOption(String word) {
        return word.length() > 1 && (word.startsWith("-") || word.startsWith("+"));
    }

    private static boolean namesShortFlag(List<String> options, char flag) {
        return options.stream().anyMatch(option -> option.startsWith("-") && !option.startsWith("--") && option.indexOf(flag) > 0);
    }

    private static List<ShellCommand> findActions(List<ShellWord> arguments, int nesting) {
        List<ShellCommand> actions = new ArrayList<>();
        int index = 0;
        while (index < arguments.size()) {
            if (EXECUTING_FIND_ACTIONS.contains(arguments.get(index).text())) {
                int end = actionEnd(arguments, index + 1);
                actions.addAll(unwrap(arguments.subList(index + 1, end), false, nesting));
                index = end;
            }
            index++;
        }
        return actions;
    }

    private static int actionEnd(List<ShellWord> arguments, int from) {
        int end = from;
        while (end < arguments.size() && !FIND_ACTION_ENDS.contains(arguments.get(end).text())) {
            end++;
        }
        return end;
    }

    private static int firstCommandWord(List<ShellWord> words) {
        int index = 0;
        while (index < words.size() && (KEYWORDS.contains(words.get(index).text()) || isAssignment(words.get(index).text()))) {
            index++;
        }
        return index;
    }

    private static List<ShellWord> afterOptions(List<ShellWord> arguments, Set<String> optionsWithValues) {
        int index = 0;
        while (index < arguments.size() && isOption(arguments.get(index))) {
            String option = arguments.get(index).text();
            index++;
            if (option.equals("--")) {
                break;
            }
            if (optionsWithValues.contains(option)) {
                index++;
            }
        }
        return arguments.subList(Math.min(index, arguments.size()), arguments.size());
    }

    private static List<ShellWord> afterFirst(List<ShellWord> arguments) {
        return arguments.isEmpty() ? arguments : arguments.subList(1, arguments.size());
    }

    private static List<ShellWord> withoutAssignments(List<ShellWord> arguments) {
        int index = 0;
        while (index < arguments.size() && isAssignment(arguments.get(index).text())) {
            index++;
        }
        return arguments.subList(index, arguments.size());
    }

    private static boolean isOption(ShellWord word) {
        return word.text().length() > 1 && word.text().startsWith("-");
    }

    private static boolean isAssignment(String word) {
        int equals = word.indexOf('=');
        if (equals < 1) {
            return false;
        }
        String name = variableName(word.substring(0, equals));
        return !name.isEmpty() && (Character.isLetter(name.charAt(0)) || name.charAt(0) == '_')
                && name.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == '_');
    }

    private static String variableName(String assigned) {
        String name = assigned.endsWith("+") ? assigned.substring(0, assigned.length() - 1) : assigned;
        int subscript = name.indexOf('[');
        return subscript > 0 && name.endsWith("]") ? name.substring(0, subscript) : name;
    }

    private static String programName(String word) {
        int slash = word.lastIndexOf('/');
        return slash < 0 ? word : word.substring(slash + 1);
    }

    private static String joined(List<ShellWord> words) {
        return words.stream().map(ShellWord::text).collect(Collectors.joining(" "));
    }
}
