package io.github.ythibos.edi.application.worker;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public record ToolCall(String tool, String command, String filePath, Path workingDirectory) {

    private static final String SHELL = "Bash";
    private static final Set<String> FILE_WRITERS = Set.of("Write", "Edit", "MultiEdit", "NotebookEdit");

    public ToolCall {
        tool = Objects.requireNonNullElse(tool, "");
    }

    public boolean runsAShellCommand() {
        return SHELL.equals(tool) && command != null;
    }

    public boolean writesAFile() {
        return FILE_WRITERS.contains(tool) && filePath != null;
    }

    public Path writtenFile() {
        Path file = Path.of(filePath);
        return file.isAbsolute() || workingDirectory == null ? file : workingDirectory.resolve(file);
    }
}
