package io.github.ythibos.edi.domain.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.ythibos.edi.domain.rule.Rule;

final class TmuxSockets {

    private static final Set<String> OPTIONS_WITH_VALUES = Set.of("-L", "-S", "-f", "-c", "-T");
    private static final List<String> SERVER_CONTROL_PREFIXES = List.of("kill", "send", "respawn");
    private static final Set<String> INPUT_COMMANDS = Set.of("send-keys", "send-prefix", "paste-buffer", "source-file", "run-shell", "if-shell",
            "pipe-pane", "display-popup", "display-menu", "respawn-pane", "respawn-window", "set-hook", "bind-key", "command-prompt", "confirm-before");
    private static final Map<String, String> WINDOW_OPENERS_AND_THEIR_VALUES = Map.of("new-window", "ceFnt", "split-window", "ceFlpt",
            "new-session", "ceFfnstxy");
    private static final String SPLIT_WINDOW = "split-window";
    private static final char FORWARDS_ITS_INPUT = 'I';
    private static final Set<String> OPTION_SETTERS = Set.of("set-option", "set-window-option");
    private static final Set<String> OPTIONS_THAT_RUN_COMMANDS = Set.of("default-command", "default-shell", "command-alias");
    private static final String SHELL_FORMAT = "#(";
    private static final String COMMAND_SEPARATOR = ";";
    private static final String ESCAPED_SEPARATOR = "\\;";
    private static final String END_OF_OPTIONS = "--";

    private TmuxSockets() {
    }

    static Optional<GuardRefusal> refusalFor(ShellCommand command, String ediSocket, boolean forAWorker, SocketPaths socketPaths) {
        if (!command.runs("tmux")) {
            return Optional.empty();
        }
        Invocation tmux = Invocation.of(command.argumentTexts());
        if (tmux.onlyPrintsItsVersion()) {
            return Optional.empty();
        }
        if (!tmux.namesItsServer()) {
            return Optional.of(new GuardRefusal(Rule.TMUX_WITHOUT_SOCKET,
                    "tmux without -L or -S reaches whichever server it finds, often the captain's own; name the socket with -L or -S (I-31)."));
        }
        if (tmux.isOn(ediSocket, socketPaths) && tmux.controlsTheServer()) {
            return Optional.of(new GuardRefusal(Rule.EDI_SERVER_CONTROL,
                    "the " + ediSocket + " tmux server and its windows belong to the core: message a worker with edi steer and never send it keys or kill its windows (I-28, I-31)."));
        }
        if (forAWorker && tmux.sendsInput()) {
            return Optional.of(new GuardRefusal(Rule.TMUX_INPUT,
                    "this tmux command types, pastes or runs commands into a pane, and on the captain's server that pane may be the primary session,"
                            + " where typed text counts as the captain's word; a worker never sends input to a tmux pane, on any socket:"
                            + " report with edi status and let the primary steer (I-06, I-31)."));
        }
        return Optional.empty();
    }

    private record Invocation(String socketName, String socketPath, boolean printsVersion, boolean controlMode, List<String> arguments,
            List<TmuxCall> calls) {

        static Invocation of(List<String> arguments) {
            String socketName = null;
            String socketPath = null;
            boolean printsVersion = false;
            boolean controlMode = false;
            int index = 0;
            while (index < arguments.size() && arguments.get(index).startsWith("-") && arguments.get(index).length() > 1) {
                String option = arguments.get(index);
                String flag = option.substring(0, 2);
                String value = valueOf(option, arguments, index);
                if (flag.equals("-L")) {
                    socketName = value;
                } else if (flag.equals("-S")) {
                    socketPath = value;
                } else if (!OPTIONS_WITH_VALUES.contains(flag)) {
                    printsVersion |= option.contains("V");
                    controlMode |= option.contains("C");
                }
                index += OPTIONS_WITH_VALUES.contains(flag) && option.length() == 2 ? 2 : 1;
            }
            List<String> commands = arguments.subList(Math.min(index, arguments.size()), arguments.size());
            return new Invocation(socketName, socketPath, printsVersion, controlMode, commands, callsIn(commands));
        }

        boolean onlyPrintsItsVersion() {
            return printsVersion;
        }

        boolean namesItsServer() {
            return socketName != null || socketPath != null;
        }

        boolean sendsInput() {
            return controlMode || calls.stream().anyMatch(TmuxCall::sendsInput) || arguments.stream().anyMatch(word -> word.contains(SHELL_FORMAT));
        }

        boolean isOn(String ediSocket, SocketPaths socketPaths) {
            return ediSocket.equals(socketName) || socketPath != null && ediSocket.equals(lastSegmentOf(socketPaths.realPathOf(socketPath)));
        }

        boolean controlsTheServer() {
            return calls.stream().flatMap(call -> call.command().stream()).anyMatch(name -> SERVER_CONTROL_PREFIXES.stream().anyMatch(name::startsWith));
        }

        private static String valueOf(String option, List<String> arguments, int index) {
            if (option.length() > 2) {
                return option.substring(2);
            }
            return index + 1 < arguments.size() ? arguments.get(index + 1) : "";
        }

        private static List<TmuxCall> callsIn(List<String> commands) {
            List<TmuxCall> calls = new ArrayList<>();
            List<String> words = new ArrayList<>();
            for (String word : commands) {
                if (word.equals(COMMAND_SEPARATOR)) {
                    endTheCall(words, calls);
                } else if (word.endsWith(COMMAND_SEPARATOR) && !word.endsWith(ESCAPED_SEPARATOR)) {
                    words.add(word.substring(0, word.length() - COMMAND_SEPARATOR.length()));
                    endTheCall(words, calls);
                } else {
                    words.add(word);
                }
            }
            endTheCall(words, calls);
            return calls;
        }

        private static void endTheCall(List<String> words, List<TmuxCall> calls) {
            if (!words.isEmpty()) {
                calls.add(TmuxCall.of(words));
                words.clear();
            }
        }

        private static String lastSegmentOf(String path) {
            String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            return trimmed.substring(trimmed.lastIndexOf('/') + 1);
        }
    }

    private record TmuxCall(Optional<String> command, List<String> arguments) {

        static TmuxCall of(List<String> words) {
            return new TmuxCall(TmuxCommandTable.commandNamed(words.getFirst()), List.copyOf(words.subList(1, words.size())));
        }

        boolean sendsInput() {
            return command.filter(name -> INPUT_COMMANDS.contains(name) || opensAWindowRunningACommand(name) || setsAnOptionThatRunsCommands(name))
                    .isPresent();
        }

        private boolean opensAWindowRunningACommand(String name) {
            String optionsWithValues = WINDOW_OPENERS_AND_THEIR_VALUES.get(name);
            if (optionsWithValues == null) {
                return false;
            }
            int index = 0;
            while (index < arguments.size() && arguments.get(index).startsWith("-") && arguments.get(index).length() > 1) {
                String cluster = arguments.get(index);
                if (cluster.equals(END_OF_OPTIONS)) {
                    return index + 1 < arguments.size();
                }
                if (name.equals(SPLIT_WINDOW) && cluster.indexOf(FORWARDS_ITS_INPUT) > 0) {
                    return true;
                }
                index += takesTheNextWord(cluster, optionsWithValues) ? 2 : 1;
            }
            return index < arguments.size();
        }

        private static boolean takesTheNextWord(String cluster, String optionsWithValues) {
            for (int position = 1; position < cluster.length(); position++) {
                if (optionsWithValues.indexOf(cluster.charAt(position)) >= 0) {
                    return position == cluster.length() - 1;
                }
            }
            return false;
        }

        private boolean setsAnOptionThatRunsCommands(String name) {
            return OPTION_SETTERS.contains(name) && arguments.stream().map(TmuxCall::optionName).anyMatch(OPTIONS_THAT_RUN_COMMANDS::contains);
        }

        private static String optionName(String argument) {
            int index = argument.indexOf('[');
            return index < 0 ? argument : argument.substring(0, index);
        }
    }
}
