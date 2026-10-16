package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.process.ProgramRun;

@Component
class Tmux {

    private static final String SESSION = "workers";
    private static final String OPENED_FORMAT = "#{window_id} #{pane_pid}";
    private static final String SERVER_FORMAT = "#{pid} #{start_time}";
    private static final String COLUMNS = "200";
    private static final String ROWS = "50";

    private final ExternalPrograms programs;
    private final String socket;

    Tmux(ExternalPrograms programs, @Value("${edi.workers.socket}") String socket) {
        this.programs = programs;
        this.socket = socket;
    }

    String socket() {
        return socket;
    }

    synchronized OpenedWindow openWindow(String name, Path directory, Map<String, String> environment, List<String> command) {
        List<String> arguments = new ArrayList<>(hasTheWorkersSession()
                ? List.of("new-window", "-d", "-t", SESSION + ":", "-n", name)
                : List.of("new-session", "-d", "-s", SESSION, "-n", name, "-x", COLUMNS, "-y", ROWS));
        arguments.addAll(List.of("-c", directory.toString(), "-P", "-F", OPENED_FORMAT));
        new TreeMap<>(environment).forEach((variable, value) -> arguments.addAll(List.of("-e", variable + "=" + value)));
        arguments.add("--");
        arguments.addAll(command);
        String[] opened = tmux(arguments).strip().split(" ");
        return new OpenedWindow(opened[0], Long.parseLong(opened[1]));
    }

    ServerIdentity server() {
        String[] identity = tmux(List.of("display-message", "-p", "-t", SESSION + ":", SERVER_FORMAT)).strip().split(" ");
        return new ServerIdentity(Long.parseLong(identity[0]), Instant.ofEpochSecond(Long.parseLong(identity[1])));
    }

    boolean serves(ServerIdentity identity) {
        return hasTheWorkersSession() && server().equals(identity);
    }

    Set<String> windowIds() {
        ProgramRun windows = run(List.of("list-windows", "-a", "-F", "#{window_id}"));
        return windows.succeeded() ? windows.output().lines().map(String::strip).collect(Collectors.toSet()) : Set.of();
    }

    List<String> visibleLines(String windowId) {
        return tmux(List.of("capture-pane", "-p", "-t", windowId)).lines().toList();
    }

    void typeLiterally(String windowId, String text) {
        tmux(List.of("send-keys", "-t", windowId, "-l", "--", text));
    }

    void pressEnter(String windowId) {
        tmux(List.of("send-keys", "-t", windowId, "Enter"));
    }

    void pressEscape(String windowId) {
        tmux(List.of("send-keys", "-t", windowId, "Escape"));
    }

    void closeWindow(String windowId) {
        tmux(List.of("kill-window", "-t", windowId));
    }

    private boolean hasTheWorkersSession() {
        return run(List.of("has-session", "-t", "=" + SESSION)).succeeded();
    }

    private String tmux(List<String> arguments) {
        ProgramRun run = run(arguments);
        if (!run.succeeded()) {
            throw new ProgramFailure("tmux -L " + socket + " " + arguments.getFirst() + " failed: " + run.errors().strip());
        }
        return run.output();
    }

    private ProgramRun run(List<String> arguments) {
        List<String> onTheWorkersSocket = new ArrayList<>(List.of("-L", socket));
        onTheWorkersSocket.addAll(arguments);
        return programs.run(Program.TMUX, Path.of(System.getProperty("java.io.tmpdir")), onTheWorkersSocket);
    }

    record OpenedWindow(String windowId, long panePid) {
    }

    record ServerIdentity(long pid, Instant startedAt) {
    }
}
