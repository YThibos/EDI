package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class TmuxSockets {

    private static final Set<String> OPTIONS_WITH_VALUES = Set.of("-L", "-S", "-f", "-c", "-T");
    private static final List<String> SERVER_CONTROL_PREFIXES = List.of("kill", "send", "respawn");
    private static final String COMMAND_SEPARATOR = ";";

    private TmuxSockets() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command, String ediSocket) {
        if (!command.runs("tmux")) {
            return Optional.empty();
        }
        Invocation tmux = Invocation.of(command.argumentTexts());
        if (tmux.onlyPrintsItsVersion()) {
            return Optional.empty();
        }
        if (!tmux.namesItsServer()) {
            return Optional.of(new GuardRefusal(GuardRule.TMUX_WITHOUT_SOCKET,
                    "tmux without -L or -S reaches whichever server it finds, often the captain's own; name the socket with -L or -S (I-31)."));
        }
        if (tmux.isOn(ediSocket) && tmux.controlsTheServer()) {
            return Optional.of(new GuardRefusal(GuardRule.EDI_SERVER_CONTROL,
                    "the " + ediSocket + " tmux server and its windows belong to EDI's core: message a worker with edi steer and never send it keys or kill its windows (I-28, I-31)."));
        }
        return Optional.empty();
    }

    private record Invocation(String socketName, String socketPath, boolean printsVersion, List<String> commandNames) {

        static Invocation of(List<String> arguments) {
            String socketName = null;
            String socketPath = null;
            boolean printsVersion = false;
            int index = 0;
            while (index < arguments.size() && arguments.get(index).startsWith("-") && arguments.get(index).length() > 1) {
                String option = arguments.get(index);
                String flag = option.substring(0, 2);
                String value = valueOf(option, arguments, index);
                if (flag.equals("-L")) {
                    socketName = value;
                } else if (flag.equals("-S")) {
                    socketPath = value;
                } else if (option.contains("V") && !OPTIONS_WITH_VALUES.contains(flag)) {
                    printsVersion = true;
                }
                index += OPTIONS_WITH_VALUES.contains(flag) && option.length() == 2 ? 2 : 1;
            }
            return new Invocation(socketName, socketPath, printsVersion, commandNames(arguments.subList(Math.min(index, arguments.size()), arguments.size())));
        }

        boolean onlyPrintsItsVersion() {
            return printsVersion;
        }

        boolean namesItsServer() {
            return socketName != null || socketPath != null;
        }

        boolean isOn(String ediSocket) {
            return ediSocket.equals(socketName) || socketPath != null && ediSocket.equals(lastSegmentOf(socketPath));
        }

        boolean controlsTheServer() {
            return commandNames.stream().anyMatch(name -> SERVER_CONTROL_PREFIXES.stream().anyMatch(name::startsWith));
        }

        private static String valueOf(String option, List<String> arguments, int index) {
            if (option.length() > 2) {
                return option.substring(2);
            }
            return index + 1 < arguments.size() ? arguments.get(index + 1) : "";
        }

        private static List<String> commandNames(List<String> commands) {
            List<String> names = new ArrayList<>();
            boolean atCommandName = true;
            for (String word : commands) {
                if (word.equals(COMMAND_SEPARATOR)) {
                    atCommandName = true;
                } else if (atCommandName) {
                    names.add(word);
                    atCommandName = false;
                }
            }
            return names;
        }

        private static String lastSegmentOf(String path) {
            String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            return trimmed.substring(trimmed.lastIndexOf('/') + 1);
        }
    }
}
