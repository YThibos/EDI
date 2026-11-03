package io.github.ythibos.edi.application.worker;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public record ToolCall(String tool, String command, String filePath, Path workingDirectory, String pattern, boolean answersFilledIn) {

    private static final Set<String> SHELLS = Set.of("Bash", "Monitor");
    private static final String FILE_READER = "Read";
    private static final Set<String> FILE_WRITERS = Set.of("Write", "Edit", "MultiEdit", "NotebookEdit");
    private static final String FILE_SEARCH = "Glob";
    private static final String CAPTAINS_DIALOG = "AskUserQuestion";

    public ToolCall {
        tool = Objects.requireNonNullElse(tool, "");
    }

    public ToolCall(String tool, String command, String filePath, Path workingDirectory, String pattern) {
        this(tool, command, filePath, workingDirectory, pattern, false);
    }

    public ToolCall(String tool, String command, String filePath, Path workingDirectory) {
        this(tool, command, filePath, workingDirectory, null);
    }

    public boolean runsAShellCommand() {
        return SHELLS.contains(tool) && command != null;
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

    public boolean asksTheCaptainWithItsAnswersGiven() {
        return CAPTAINS_DIALOG.equals(tool) && answersFilledIn;
    }

    public Path searchedDirectory() {
        return filePath != null ? file() : workingDirectory;
    }

    public Path file() {
        Path file = Path.of(filePath);
        return file.isAbsolute() || workingDirectory == null ? file : workingDirectory.resolve(file);
    }
}
