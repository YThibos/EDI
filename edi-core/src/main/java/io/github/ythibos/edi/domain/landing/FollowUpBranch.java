package io.github.ythibos.edi.domain.landing;

import java.util.List;
import java.util.Objects;

public record FollowUpBranch(String ref, List<BranchCommit> commitsBeyondIt, boolean holdsTheContent) {

    public FollowUpBranch {
        Objects.requireNonNull(ref, "A follow-up branch is seen at its ref on the forge.");
        commitsBeyondIt = List.copyOf(commitsBeyondIt);
    }

    public boolean carriesAll(List<BranchCommit> commits) {
        return holdsTheContent || commits.stream().allMatch(this::carries);
    }

    private boolean carries(BranchCommit commit) {
        return commitsBeyondIt.stream().noneMatch(beyond -> beyond.id().equals(commit.id()) && !beyond.sameChangeInTheBase());
    }
}
