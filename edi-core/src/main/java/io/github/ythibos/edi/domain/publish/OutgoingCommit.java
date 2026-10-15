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
}
