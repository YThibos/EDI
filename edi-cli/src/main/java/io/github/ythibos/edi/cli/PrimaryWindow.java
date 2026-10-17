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
            int opened = LocalPrograms.runAttached(newSession(launch, true), launch.directory(), Map.of());
            return opened == 0 ? LocalPrograms.runAttached(List.of("tmux", "switch-client", "-t", launch.session()), launch.directory(), Map.of()) : opened;
        }
        return LocalPrograms.runAttached(newSession(launch, false), launch.directory(), Map.of());
    }

    private static List<String> newSession(PrimaryLaunch launch, boolean detached) {
        List<String> command = new ArrayList<>(List.of("tmux", "new-session"));
        if (detached) {
            command.add("-d");
        }
        command.addAll(List.of("-s", launch.session(), "-c", launch.directory().toString()));
        command.addAll(launch.claude());
        return command;
    }
}
