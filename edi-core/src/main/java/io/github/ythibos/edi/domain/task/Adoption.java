package io.github.ythibos.edi.domain.task;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.ythibos.edi.domain.branch.BranchName;

public record Adoption(TaskId task, BranchName branch, UUID session, Instant adoptedAt) {

    public Adoption {
        Objects.requireNonNull(task, "An adoption takes over a task.");
        Objects.requireNonNull(branch, () -> task + " is adopted on the branch its work is on.");
        Objects.requireNonNull(adoptedAt, () -> task + " is adopted at a moment.");
    }

    public boolean resumesASession() {
        return session != null;
    }

    public boolean continues(BranchName otherBranch, UUID otherSession) {
        return branch.equals(otherBranch) && Objects.equals(session, otherSession);
    }
}
