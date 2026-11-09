package io.github.ythibos.edi.domain.mechanical;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.rule.Rule;

public record ExpectedChange(String description, List<String> files) {

    private static final String PARENT_STEP = "..";

    public ExpectedChange {
        description = Objects.requireNonNullElse(description, "").strip();
        files = List.copyOf(Objects.requireNonNullElse(files, List.of()));
        if (description.isEmpty() || files.isEmpty()) {
            throw new Refusal(Rule.MECHANICAL_SHAPE, "A mechanical task states its exact expected change and names every file it touches.");
        }
        files.forEach(ExpectedChange::requireRepositoryPath);
        files.forEach(ExpectedChange::requireNotProtected);
        if (new HashSet<>(files).size() < files.size()) {
            throw new IllegalArgumentException("A mechanical task names each file once.");
        }
    }

    public List<String> filesBeyond(Set<String> changedFiles) {
        return changedFiles.stream().filter(changed -> !files.contains(changed)).sorted().toList();
    }

    public boolean isAbsentFrom(Set<String> changedFiles) {
        return files.stream().noneMatch(changedFiles::contains);
    }

    private static void requireRepositoryPath(String file) {
        boolean relative = !file.isBlank() && !file.startsWith("/") && !file.contains("\\");
        boolean contained = relative && List.of(file.split("/")).stream().noneMatch(PARENT_STEP::equals);
        if (!contained) {
            throw new IllegalArgumentException("A mechanical task names files by their path inside the repository, such as src/Main.java: " + file);
        }
    }

    private static void requireNotProtected(String file) {
        ProtectedFile.of(file).ifPresent(kind -> {
            throw new Refusal(Rule.PROTECTED_FILES, "A mechanical task never edits " + kind.description() + ", and " + file + " is one; dispatch it as implementation.");
        });
    }
}
