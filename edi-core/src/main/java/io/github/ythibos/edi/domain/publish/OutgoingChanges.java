package io.github.ythibos.edi.domain.publish;

import java.util.List;

public record OutgoingChanges(List<OutgoingCommit> commits) {

    public OutgoingChanges {
        commits = List.copyOf(commits);
    }

    public boolean touchAPathUnder(String directory) {
        return commits.stream().flatMap(commit -> commit.paths().stream()).anyMatch(path -> path.startsWith(directory));
    }

    int positionOf(String commitId, String path) {
        return commits.stream()
                .filter(commit -> commit.id().equals(commitId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("A finding names commit " + commitId + ", which is not outgoing."))
                .positionOf(path);
    }
}
