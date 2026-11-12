package io.github.ythibos.edi.domain.guard;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.github.ythibos.edi.domain.rule.Rule;

final class WorkerIdentity {

    private static final Set<String> VARIABLES = Set.of("EDI_TOKEN_FILE", "EDI_ROLE", "EDI_WORKER", "EDI_TASK_ID");
    private static final Set<String> DECLARATIONS = Set.of("export", "declare", "typeset", "local", "readonly");
    private static final Set<String> FRESH_ENVIRONMENTS = Set.of("systemd-run");
    private static final String EVERY_VARIABLE = "every variable";

    private WorkerIdentity() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command) {
        Set<String> touched = new TreeSet<>(identityAmong(command.assignedVariables()));
        touched.addAll(switch (command.program()) {
            case "unset" -> identityAmong(command.argumentTexts());
            case "env" -> EnvOptions.read(command.argumentTexts()).touchedWhenRunFrom(command.argumentsFromInput());
            case "exec" -> clearedByExec(command.argumentTexts());
            default -> otherwiseTouched(command);
        });
        return touched.isEmpty() ? Optional.empty() : refused(touched);
    }

    private static List<String> identityAmong(List<String> names) {
        return names.stream().filter(VARIABLES::contains).toList();
    }

    private static List<String> clearedByExec(List<String> arguments) {
        for (String argument : arguments) {
            if (!argument.startsWith("-") || argument.equals("--")) {
                return List.of();
            }
            if (argument.indexOf('c') > 0) {
                return List.of(EVERY_VARIABLE);
            }
        }
        return List.of();
    }

    private static List<String> otherwiseTouched(ShellCommand command) {
        if (FRESH_ENVIRONMENTS.contains(command.program())) {
            return List.of(EVERY_VARIABLE);
        }
        return DECLARATIONS.contains(command.program()) ? declared(command.argumentTexts()) : List.of();
    }

    private static List<String> declared(List<String> arguments) {
        return arguments.stream()
                .filter(argument -> !argument.startsWith("-") && !argument.startsWith("+"))
                .map(argument -> CommandWrappers.assignedVariable(argument).orElse(argument))
                .filter(VARIABLES::contains)
                .toList();
    }

    private static Optional<GuardRefusal> refused(Set<String> touched) {
        return Optional.of(new GuardRefusal(Rule.WORKER_IDENTITY, "this command unsets or reassigns " + String.join(", ", touched)
                + ", which tells the edi command and the core which worker is calling; a worker calls the core only as itself, with its own token file,"
                + " and never with the primary's home token."));
    }

    private static final class EnvOptions {

        private static final List<String> LONG_OPTIONS = List.of("ignore-environment", "null", "unset", "chdir", "split-string", "block-signal",
                "default-signal", "ignore-signal", "list-signal-handling", "debug", "help", "version", "argv0");
        private static final Set<String> LONG_OPTIONS_WITH_VALUES = Set.of("unset", "chdir", "split-string", "argv0");
        private static final String SHORT_OPTIONS_WITH_VALUES = "uCSa";

        private final Set<String> touched = new TreeSet<>();
        private boolean unsets;
        private boolean reachesItsCommand;

        static EnvOptions read(List<String> arguments) {
            EnvOptions options = new EnvOptions();
            options.readFrom(arguments);
            return options;
        }

        List<String> touchedWhenRunFrom(boolean argumentsFromInput) {
            if (argumentsFromInput && (unsets || !reachesItsCommand)) {
                return List.of(EVERY_VARIABLE);
            }
            return List.copyOf(touched);
        }

        private void readFrom(List<String> arguments) {
            int index = 0;
            while (index < arguments.size()) {
                String argument = arguments.get(index);
                if (argument.equals("-")) {
                    touched.add(EVERY_VARIABLE);
                    index++;
                } else if (argument.equals("--")) {
                    index++;
                } else if (argument.startsWith("--")) {
                    index = afterLongOption(arguments, index);
                } else if (argument.startsWith("-") && argument.length() > 1) {
                    index = afterShortOptions(arguments, index);
                } else if (CommandWrappers.assignedVariable(argument).isPresent()) {
                    CommandWrappers.assignedVariable(argument).filter(VARIABLES::contains).ifPresent(touched::add);
                    index++;
                } else {
                    reachesItsCommand = true;
                    return;
                }
            }
        }

        private int afterLongOption(List<String> arguments, int index) {
            String argument = arguments.get(index).substring(2);
            int equals = argument.indexOf('=');
            String written = equals < 0 ? argument : argument.substring(0, equals);
            Optional<String> option = longOptionNamed(written);
            if (option.isEmpty()) {
                return index + 1;
            }
            if (!LONG_OPTIONS_WITH_VALUES.contains(option.get())) {
                apply(option.get(), null);
                return index + 1;
            }
            if (equals >= 0) {
                apply(option.get(), argument.substring(equals + 1));
                return index + 1;
            }
            apply(option.get(), valueAt(arguments, index + 1));
            return index + 2;
        }

        private int afterShortOptions(List<String> arguments, int index) {
            String cluster = arguments.get(index);
            for (int position = 1; position < cluster.length(); position++) {
                char option = cluster.charAt(position);
                if (SHORT_OPTIONS_WITH_VALUES.indexOf(option) >= 0) {
                    String joined = cluster.substring(position + 1);
                    apply(String.valueOf(option), joined.isEmpty() ? valueAt(arguments, index + 1) : joined);
                    return joined.isEmpty() ? index + 2 : index + 1;
                }
                apply(String.valueOf(option), null);
            }
            return index + 1;
        }

        private void apply(String option, String value) {
            switch (option) {
                case "i", "ignore-environment" -> touched.add(EVERY_VARIABLE);
                case "u", "unset" -> {
                    unsets = true;
                    touched.addAll(identityAmong(List.of(value)));
                }
                case "S", "split-string" -> {
                    EnvOptions split = read(Arrays.asList(value.strip().split("\\s+")));
                    touched.addAll(split.touched);
                    unsets |= split.unsets;
                    reachesItsCommand |= split.reachesItsCommand;
                }
                default -> {
                }
            }
        }

        private static Optional<String> longOptionNamed(String written) {
            if (LONG_OPTIONS.contains(written)) {
                return Optional.of(written);
            }
            List<String> candidates = LONG_OPTIONS.stream().filter(option -> option.startsWith(written)).toList();
            return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
        }

        private static String valueAt(List<String> arguments, int index) {
            return index < arguments.size() ? arguments.get(index) : "";
        }
    }
}
