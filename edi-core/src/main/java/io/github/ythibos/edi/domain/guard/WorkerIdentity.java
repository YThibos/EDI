package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class WorkerIdentity {

    private static final Set<String> VARIABLES = Set.of("EDI_TOKEN_FILE", "EDI_ROLE", "EDI_WORKER", "EDI_TASK_ID");
    private static final Set<String> DECLARATIONS = Set.of("export", "declare", "typeset", "local", "readonly");
    private static final Set<String> ENVIRONMENT_CLEARINGS = Set.of("-", "-i", "--ignore-environment");
    private static final Set<String> ENVIRONMENT_UNSETTINGS = Set.of("-u", "--unset");
    private static final Set<String> ENVIRONMENT_SPLITS = Set.of("-S", "--split-string");
    private static final Set<String> ENVIRONMENT_OPTIONS_WITH_VALUES = Set.of("-C", "--chdir");
    private static final String EVERY_VARIABLE = "every variable";

    private WorkerIdentity() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command) {
        Set<String> touched = new TreeSet<>(identityAmong(command.assignedVariables()));
        touched.addAll(switch (command.program()) {
            case "unset" -> identityAmong(command.argumentTexts());
            case "env" -> touchedByEnv(command.argumentTexts());
            default -> DECLARATIONS.contains(command.program()) ? declared(command.argumentTexts()) : List.of();
        });
        return touched.isEmpty() ? Optional.empty() : refused(touched);
    }

    private static List<String> identityAmong(List<String> names) {
        return names.stream().filter(VARIABLES::contains).toList();
    }

    private static List<String> touchedByEnv(List<String> arguments) {
        List<String> touched = new ArrayList<>();
        int index = 0;
        while (index < arguments.size()) {
            String argument = arguments.get(index);
            if (ENVIRONMENT_CLEARINGS.contains(argument) || isAShortOptionCluster(argument) && argument.indexOf('i') > 0) {
                return List.of(EVERY_VARIABLE);
            }
            if (ENVIRONMENT_UNSETTINGS.contains(argument) || ENVIRONMENT_SPLITS.contains(argument)) {
                touched.addAll(valueTouches(argument, index + 1 < arguments.size() ? arguments.get(index + 1) : ""));
                index += 2;
            } else if (argument.startsWith("--unset=") || argument.startsWith("-u") && argument.length() > 2) {
                touched.addAll(identityAmong(List.of(argument.substring(argument.startsWith("--") ? "--unset=".length() : 2))));
                index++;
            } else if (ENVIRONMENT_OPTIONS_WITH_VALUES.contains(argument)) {
                index += 2;
            } else if (argument.startsWith("-") || CommandWrappers.assignedVariable(argument).isPresent()) {
                CommandWrappers.assignedVariable(argument).filter(VARIABLES::contains).ifPresent(touched::add);
                index++;
            } else {
                break;
            }
        }
        return touched;
    }

    private static boolean isAShortOptionCluster(String argument) {
        return argument.length() > 1 && argument.startsWith("-") && !argument.startsWith("--") && argument.chars().skip(1).allMatch(Character::isLetter);
    }

    private static List<String> valueTouches(String option, String value) {
        return ENVIRONMENT_SPLITS.contains(option) ? touchedByEnv(Arrays.asList(value.strip().split("\\s+"))) : identityAmong(List.of(value));
    }

    private static List<String> declared(List<String> arguments) {
        return arguments.stream()
                .filter(argument -> !argument.startsWith("-") && !argument.startsWith("+"))
                .map(argument -> CommandWrappers.assignedVariable(argument).orElse(argument))
                .filter(VARIABLES::contains)
                .toList();
    }

    private static Optional<GuardRefusal> refused(Set<String> touched) {
        return Optional.of(new GuardRefusal(GuardRule.WORKER_IDENTITY, "this command unsets or reassigns " + String.join(", ", touched)
                + ", which tells the edi command and the core which worker is calling; a worker calls the core only as itself, with its own token file,"
                + " and never with the primary's home token."));
    }
}
