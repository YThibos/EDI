package io.github.ythibos.edi.domain.publish;

import java.util.List;

public record OutgoingCommit(
        String id,
        String author,
        String committer,
        String message,
        List<String> paths,
        List<AddedLine> addedLines) {

    public OutgoingCommit {
        paths = List.copyOf(paths);
        addedLines = List.copyOf(addedLines);
    }

    int positionOf(String path) {
        int index = paths.indexOf(path);
        if (index < 0) {
            throw new IllegalStateException("A finding names a path that commit " + id + " does not publish.");
        }
        return index + 1;
    }
}
