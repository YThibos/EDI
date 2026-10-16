package io.github.ythibos.edi.domain.guard;

import java.util.List;
import java.util.Set;

final class ShellInvocation {

    private static final String SHORT_OPTIONS = "abcefhiklmnoprstuvxBCDEHOPT";
    private static final Set<String> OPTION_ENDS = Set.of("--", "-");
    private static final Set<String> LONG_OPTIONS_WITH_A_VALUE = Set.of("--rcfile", "--init-file");
    private static final Set<String> ANSWERING_OPTIONS = Set.of("--version", "--help");
    private static final Set<String> LONG_FLAGS = Set.of("--login", "--noprofile", "--norc", "--posix", "--noediting",
            "--restricted", "--verbose", "--debugger", "--dump-strings", "--dump-po-strings");

    private final List<ShellWord> arguments;
    private int position;
    private boolean answersOnly;
    private boolean runsACommandString;
    private boolean readsStandardInput;

    private ShellInvocation(List<ShellWord> arguments) {
        this.arguments = arguments;
    }

    static List<ShellCommand> commandsRunBy(List<ShellWord> words, int nesting) {
        ShellInvocation invocation = new ShellInvocation(words.stream().filter(word -> !word.redirectionTarget()).toList());
        invocation.readOptions();
        return invocation.commandsRun(nesting);
    }

    private void readOptions() {
        while (position < arguments.size() && isOption(arguments.get(position).text())) {
            String option = arguments.get(position).text();
            position++;
            if (OPTION_ENDS.contains(option)) {
                return;
            }
            if (option.startsWith("--")) {
                readLongOption(option);
            } else {
                readShortOptions(option);
            }
        }
    }

    private List<ShellCommand> commandsRun(int nesting) {
        if (answersOnly) {
            return List.of();
        }
        if (readsStandardInput || position >= arguments.size()) {
            throw new UnreadableCommand("The shell reads its script from standard input, which the guard cannot see.");
        }
        return runsACommandString ? ShellScript.commandsIn(arguments.get(position).text(), nesting + 1) : List.of();
    }

    private static boolean isOption(String word) {
        return word.startsWith("-") || word.startsWith("+") && word.length() > 1;
    }

    private void readLongOption(String option) {
        if (ANSWERING_OPTIONS.contains(option)) {
            answersOnly = true;
        } else if (LONG_OPTIONS_WITH_A_VALUE.contains(option)) {
            position++;
        } else if (!LONG_FLAGS.contains(option)) {
            throw unknown(option);
        }
    }

    private void readShortOptions(String group) {
        for (char letter : group.substring(1).toCharArray()) {
            if (SHORT_OPTIONS.indexOf(letter) < 0) {
                throw unknown(group);
            }
            runsACommandString |= letter == 'c';
            readsStandardInput |= letter == 's';
            if (letter == 'o' || letter == 'O') {
                position++;
            }
        }
    }

    private static UnreadableCommand unknown(String option) {
        return new UnreadableCommand("The guard does not know the shell option " + option + ", so it cannot tell what the shell runs.");
    }
}
