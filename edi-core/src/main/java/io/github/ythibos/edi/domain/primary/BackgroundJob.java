package io.github.ythibos.edi.domain.primary;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.guard.ShellCommand;
import io.github.ythibos.edi.domain.guard.ShellScript;
import io.github.ythibos.edi.domain.guard.UnreadableCommand;

public record BackgroundJob(String type, String status, String command) {

    private static final String SHELL = "shell";
    private static final Set<String> IN_FLIGHT = Set.of("running", "pending");

    public BackgroundJob {
        type = Objects.requireNonNullElse(type, "");
        status = Objects.requireNonNullElse(status, "");
        command = Objects.requireNonNullElse(command, "");
    }

    public static boolean anyWaitsForEdi(List<BackgroundJob> jobs) {
        return jobs.stream().anyMatch(BackgroundJob::waitsForEdi);
    }

    public boolean waitsForEdi() {
        return type.equals(SHELL) && IN_FLIGHT.contains(status) && runsEdiWait();
    }

    private boolean runsEdiWait() {
        try {
            return ShellScript.commandsIn(command).stream().anyMatch(BackgroundJob::isEdiWait);
        } catch (UnreadableCommand unreadable) {
            return false;
        }
    }

    private static boolean isEdiWait(ShellCommand command) {
        List<String> arguments = command.argumentTexts();
        return command.runs("edi") && !arguments.isEmpty() && arguments.getFirst().equals("wait");
    }
}
