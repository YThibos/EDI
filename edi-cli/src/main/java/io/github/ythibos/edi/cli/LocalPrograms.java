package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class LocalPrograms {

    private LocalPrograms() {
    }

    @SuppressWarnings("java:S4036") // the captain's own tools are found on his PATH, as the core finds git and tmux (ADR-048)
    static int runAttached(List<String> command, Path directory, Map<String, String> environment) {
        try {
            ProcessBuilder launch = new ProcessBuilder(command).directory(directory.toFile()).inheritIO();
            launch.environment().putAll(environment);
            return launch.start().waitFor();
        } catch (IOException unstartable) {
            throw new CommandFailure(command.getFirst() + " could not run: " + unstartable.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CommandFailure(command.getFirst() + " was interrupted.");
        }
    }

    @SuppressWarnings("java:S4036") // the captain's own tools are found on his PATH, as the core finds git and tmux (ADR-048)
    static String outputOf(List<String> command, Path directory) {
        try {
            Process run = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
            run.getOutputStream().close();
            String output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (run.waitFor() != 0) {
                throw new CommandFailure(String.join(" ", command) + " failed: " + output.strip());
            }
            return output;
        } catch (IOException unstartable) {
            throw new CommandFailure(command.getFirst() + " could not run: " + unstartable.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CommandFailure(command.getFirst() + " was interrupted.");
        }
    }

    @SuppressWarnings("java:S4036") // setsid comes with every Linux the harness runs on, from the captain's own PATH
    static void startDetached(List<String> command, Path log, Map<String, String> environment) {
        try {
            ProcessBuilder launch = new ProcessBuilder(command).redirectOutput(Redirect.appendTo(log.toFile())).redirectErrorStream(true);
            launch.environment().putAll(environment);
            launch.start().getOutputStream().close();
        } catch (IOException unstartable) {
            throw new CommandFailure(command.getFirst() + " could not start: " + unstartable.getMessage());
        }
    }
}
