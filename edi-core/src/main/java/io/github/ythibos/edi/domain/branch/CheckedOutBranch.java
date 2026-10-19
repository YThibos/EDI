package io.github.ythibos.edi.domain.branch;

public record CheckedOutBranch(String name, boolean hasUncommittedChanges) {

    public CheckedOutBranch {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A checked-out branch has a name, or the commit a detached head is at.");
        }
    }
}
