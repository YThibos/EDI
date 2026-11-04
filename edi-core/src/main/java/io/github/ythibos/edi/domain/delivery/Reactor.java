package io.github.ythibos.edi.domain.delivery;

import java.util.List;
import java.util.Optional;

import io.github.ythibos.edi.domain.publish.AddedLine;

public record Reactor(List<BuildFile> buildFiles) {

    public Reactor {
        buildFiles = List.copyOf(buildFiles);
    }

    boolean admits(AddedLine line) {
        return parentOf(line.path()).filter(parent -> parent.spans(line.number())).filter(this::namesOneOfItsModules).isPresent();
    }

    private Optional<ParentReference> parentOf(String path) {
        return buildFiles.stream().filter(file -> file.path().equals(path)).findFirst().flatMap(BuildFile::parent);
    }

    private boolean namesOneOfItsModules(ParentReference parent) {
        return buildFiles.stream().anyMatch(file -> file.own().equals(parent.coordinates()));
    }
}
