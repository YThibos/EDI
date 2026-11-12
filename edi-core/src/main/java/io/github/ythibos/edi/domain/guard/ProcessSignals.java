package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.ythibos.edi.domain.rule.Rule;

final class ProcessSignals {

    private static final Set<String> PATTERN_KILLERS = Set.of("pkill", "killall", "skill", "slay");
    private static final Set<String> PATTERN_LOOKUPS = Set.of("pgrep", "pidof");
    private static final Set<String> PROCESS_LOOKUPS = Set.of("pgrep", "pidof", "ps", "lsof", "fuser");
    private static final Set<String> SIGNAL_OPTIONS_WITH_VALUES = Set.of("-s", "-n");
    private static final Set<String> SIGNAL_LISTINGS = Set.of("-l", "-L");
    private static final String CAPTURED_PIDS_ONLY = "; kill only a process id you started and captured (I-30).";

    private ProcessSignals() {
    }

    static Optional<GuardRefusal> refusalFor(List<ShellCommand> commands) {
        return commands.stream()
                .map(ProcessSignals::refusalFor)
                .flatMap(Optional::stream)
                .findFirst()
                .or(() -> lookupFeedingAKill(commands));
    }

    private static Optional<GuardRefusal> refusalFor(ShellCommand command) {
        if (PATTERN_KILLERS.contains(command.program())) {
            return refused(command.program() + " signals every process whose name matches a pattern, which reaches other workers and the captain's own sessions");
        }
        if (command.runs("fuser") && command.argumentTexts().stream().anyMatch(ProcessSignals::isFuserKill)) {
            return refused("fuser -k signals every process that uses a file");
        }
        return command.runs("kill") ? killRefusal(command) : Optional.empty();
    }

    private static Optional<GuardRefusal> killRefusal(ShellCommand kill) {
        if (kill.argumentsFromInput()) {
            return refused("kill fed from a pipe signals whatever the pipe found");
        }
        for (ShellWord target : killTargets(kill.arguments())) {
            if (target.substitutions().stream().anyMatch(ProcessSignals::looksUpProcesses)) {
                return refused("kill $(pgrep ...) signals the processes a lookup by pattern found");
            }
            if (!target.isSubstituted() && isProcessGroup(target.text())) {
                return refused("kill " + target.text() + " signals a whole process group");
            }
        }
        return Optional.empty();
    }

    private static Optional<GuardRefusal> lookupFeedingAKill(List<ShellCommand> commands) {
        boolean looksUpByPattern = commands.stream().anyMatch(command -> PATTERN_LOOKUPS.contains(command.program()));
        boolean kills = commands.stream().anyMatch(command -> command.runs("kill"));
        return looksUpByPattern && kills ? refused("a command line that looks processes up by pattern and kills signals what the lookup found") : Optional.empty();
    }

    private static List<ShellWord> killTargets(List<ShellWord> arguments) {
        int index = 0;
        boolean signalNamed = false;
        while (index < arguments.size()) {
            ShellWord argument = arguments.get(index);
            String text = argument.text();
            if (SIGNAL_LISTINGS.contains(text)) {
                return List.of();
            }
            if (text.equals("--")) {
                index++;
                break;
            }
            if (SIGNAL_OPTIONS_WITH_VALUES.contains(text)) {
                index += 2;
                signalNamed = true;
            } else if (!signalNamed && text.length() > 1 && text.startsWith("-") && !argument.isSubstituted()) {
                index++;
                signalNamed = true;
            } else {
                break;
            }
        }
        return arguments.subList(Math.min(index, arguments.size()), arguments.size());
    }

    private static boolean looksUpProcesses(ShellCommand command) {
        return PROCESS_LOOKUPS.contains(command.program());
    }

    private static boolean isProcessGroup(String target) {
        return target.equals("0") || target.startsWith("-");
    }

    private static boolean isFuserKill(String argument) {
        return argument.equals("--kill") || !argument.startsWith("--") && argument.startsWith("-") && argument.indexOf('k') > 0;
    }

    private static Optional<GuardRefusal> refused(String what) {
        return Optional.of(new GuardRefusal(Rule.SIGNAL_BY_PATTERN, what + CAPTURED_PIDS_ONLY));
    }
}
