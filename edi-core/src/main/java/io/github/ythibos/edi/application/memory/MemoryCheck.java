package io.github.ythibos.edi.application.memory;

import java.nio.file.Path;
import java.util.List;

import io.github.ythibos.edi.domain.memory.MemoryFile;
import io.github.ythibos.edi.domain.memory.StartupMemory;

public record MemoryCheck(List<FileEstimate> files, int estimatedTokens, int budgetTokens, List<String> duplicatedHeadings, boolean passes) {

    public MemoryCheck {
        files = List.copyOf(files);
        duplicatedHeadings = List.copyOf(duplicatedHeadings);
    }

    static MemoryCheck of(StartupMemory memory, Path directory) {
        return new MemoryCheck(memory.files().stream().map(file -> FileEstimate.of(file, directory)).toList(), memory.estimatedTokens(),
                memory.budgetTokens(), memory.duplicatedHeadings(), memory.passes());
    }

    public boolean fitsItsBudget() {
        return estimatedTokens <= budgetTokens;
    }

    public record FileEstimate(String name, Path path, int characters, int estimatedTokens) {

        static FileEstimate of(MemoryFile file, Path directory) {
            return new FileEstimate(file.name(), directory.resolve(file.name()), file.text().length(), file.estimatedTokens());
        }
    }
}
