package io.github.ythibos.edi.application.worker;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class SocketPathWords {

    private static final String WORKING_DIRECTORY = "PWD";
    private static final String TMUX_TEMPORARY_DIRECTORY = "TMUX_TMPDIR";
    private static final String TMUX_DEFAULT_DIRECTORY = "/tmp";

    private SocketPathWords() {
    }

    static String expanded(String written, Path workingDirectory) {
        String tmuxDirectory = Objects.requireNonNullElse(System.getenv(TMUX_TEMPORARY_DIRECTORY), TMUX_DEFAULT_DIRECTORY);
        String inTheWorkingDirectory = workingDirectory == null ? written : leading(written, WORKING_DIRECTORY, workingDirectory.toString());
        return leading(inTheWorkingDirectory, TMUX_TEMPORARY_DIRECTORY, tmuxDirectory);
    }

    private static String leading(String written, String variable, String value) {
        for (String form : List.of("$" + variable, "${" + variable + "}")) {
            if (written.equals(form) || written.startsWith(form + "/")) {
                return value + written.substring(form.length());
            }
        }
        return written;
    }
}
