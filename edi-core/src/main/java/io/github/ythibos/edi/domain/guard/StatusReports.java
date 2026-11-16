package io.github.ythibos.edi.domain.guard;

import java.util.List;

public final class StatusReports {

    private static final String EDI = "edi";
    private static final String STATUS = "status";
    private static final String EXPANSION = "$";
    private static final String FILE_OF_ARGUMENTS = "@";

    private StatusReports() {
    }

    public static boolean isAPlainReport(String script) {
        try {
            List<ShellCommand> commands = ShellScript.commandsIn(script);
            return commands.size() == 1 && reportsAlone(commands.getFirst());
        } catch (UnreadableCommand unreadable) {
            return false;
        }
    }

    private static boolean reportsAlone(ShellCommand command) {
        List<String> arguments = command.argumentTexts();
        return command.programAsWritten().equals(EDI) && !arguments.isEmpty() && arguments.getFirst().equals(STATUS) && takesNothingFromElsewhere(command);
    }

    private static boolean takesNothingFromElsewhere(ShellCommand command) {
        return !command.argumentsFromInput() && command.assignedVariables().isEmpty()
                && command.arguments().stream().noneMatch(StatusReports::takesFromElsewhere);
    }

    private static boolean takesFromElsewhere(ShellWord word) {
        return word.redirectionTarget() || word.isSubstituted() || word.text().contains(EXPANSION) || word.text().startsWith(FILE_OF_ARGUMENTS);
    }
}
