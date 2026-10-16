package io.github.ythibos.edi.domain.landing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record LandedWork(
        String baseRef,
        List<String> uncommittedPaths,
        List<String> nestedRepositories,
        List<BranchCommit> commitsBeyondTheBase,
        boolean contentInTheBase,
        CaptainsCopy captainsCopy) {

    public LandedWork {
        Objects.requireNonNull(baseRef, "Landed work is judged against a base.");
        uncommittedPaths = List.copyOf(uncommittedPaths);
        nestedRepositories = List.copyOf(nestedRepositories);
        commitsBeyondTheBase = List.copyOf(commitsBeyondTheBase);
        Objects.requireNonNull(captainsCopy, "Landed work knows whether the captain's checkout must hold the branch.");
    }

    public boolean isLanded() {
        return uncommittedPaths.isEmpty()
                && nestedRepositories.isEmpty()
                && unlandedCommits().isEmpty()
                && captainsCopy != CaptainsCopy.LACKS_THE_HEAD;
    }

    public List<BranchCommit> unlandedCommits() {
        if (contentInTheBase) {
            return List.of();
        }
        return commitsBeyondTheBase.stream().filter(commit -> !commit.sameChangeInTheBase()).toList();
    }

    public List<String> findings() {
        List<String> findings = new ArrayList<>();
        uncommittedPaths.forEach(path -> findings.add(path + " is not committed"));
        nestedRepositories.forEach(repository -> findings.add(repository + " is a repository nested in the copy, and nothing of it is landed"));
        unlandedCommits().forEach(commit -> findings.add("commit " + commit.shortId() + " \"" + commit.subject() + "\" is not in " + baseRef));
        if (captainsCopy == CaptainsCopy.LACKS_THE_HEAD) {
            findings.add("the captain's checkout does not hold the branch's head");
        }
        return List.copyOf(findings);
    }
}
