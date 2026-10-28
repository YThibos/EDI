package io.github.ythibos.edi.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PrimaryWindow {

    private final boolean insideTmux;

    PrimaryWindow(Map<String, String> environment) {
        String tmux = environment.get("TMUX");
        this.insideTmux = tmux != null && !tmux.isBlank();
    }

    int open(PrimaryLaunch launch) {
        if (insideTmux) {
            int opened = LocalPrograms.runAttached(newSession(launch), launch.directory(), Map.of());
            return opened == 0 ? switchTo(launch) : opened;
        }
        return LocalPrograms.runAttached(newSession(launch), launch.directory(), Map.of());
    }

    List<String> newSession(PrimaryLaunch launch) {
        List<String> command = new ArrayList<>(List.of("tmux", "new-session"));
        if (insideTmux) {
            command.add("-d");
        }
        command.addAll(List.of("-s", launch.session(), "-c", launch.directory().toString()));
        command.addAll(launch.claude());
        command.addAll(terminalTitledBySessionName(launch.session()));
        return command;
    }

    private static List<String> terminalTitledBySessionName(String session) {
        return List.of(";", "set-option", "-t", session, "set-titles", "on", ";", "set-option", "-t", session, "set-titles-string", "#S");
    }

    private static int switchTo(PrimaryLaunch launch) {
        return LocalPrograms.runAttached(List.of("tmux", "switch-client", "-t", launch.session()), launch.directory(), Map.of());
    }
}
