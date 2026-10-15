package io.github.ythibos.edi.domain.publish;

import java.util.List;

public record OutgoingChanges(List<OutgoingCommit> commits) {

    public OutgoingChanges {
        commits = List.copyOf(commits);
    }
}
