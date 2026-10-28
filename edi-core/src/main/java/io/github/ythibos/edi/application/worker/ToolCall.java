package io.github.ythibos.edi.application.worker;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public record ToolCall(String tool, String command, String filePath, Path workingDirectory, String pattern) {

    private static final String SHELL = "Bash";
    private static final String FILE_READER = "Read";
    private static final Set<String> FILE_WRITERS = Set.of("Write", "Edit", "MultiEdit", "NotebookEdit");
    private static final String FILE_SEARCH = "Glob";

    public ToolCall {
        tool = Objects.requireNonNullElse(tool, "");
    }

    public ToolCall(String tool, String command, String filePath, Path workingDirectory) {
        this(tool, command, filePath, workingDirectory, null);
    }

    public boolean runsAShellCommand() {
        return SHELL.equals(tool) && command != null;
    }

    public boolean writesAFile() {
        return FILE_WRITERS.contains(tool) && filePath != null;
    }

    public boolean readsAFile() {
        return FILE_READER.equals(tool) && filePath != null;
    }

    public boolean searchesForFiles() {
        return FILE_SEARCH.equals(tool) && pattern != null;
    }

    public Path searchedDirectory() {
        return filePath != null ? file() : workingDirectory;
    }

    public Path file() {
        Path file = Path.of(filePath);
        return file.isAbsolute() || workingDirectory == null ? file : workingDirectory.resolve(file);
    }
}
