package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.memory.MemoryFile;

@Component
public class MemoryFiles {

    public static final List<String> LOADED_AT_SESSION_START = List.of("profile.md", "machine.md", "learnings.md");
    public static final String ARCHIVE = "archive.md";

    private final Path directory;

    public MemoryFiles(EdiHome home) {
        this.directory = home.memory();
    }

    public List<MemoryFile> loadedAtSessionStart() {
        return LOADED_AT_SESSION_START.stream().map(this::read).toList();
    }

    public Path directory() {
        return directory;
    }

    private MemoryFile read(String name) {
        return new MemoryFile(name, ConfigurationFile.contentOf(directory.resolve(name)).orElse(""));
    }
}
