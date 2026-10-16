package io.github.ythibos.edi.domain.landing;

import java.util.Objects;

public record BranchCommit(String id, String subject, boolean sameChangeInTheBase) {

    private static final int SHORT_ID = 12;

    public BranchCommit {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A branch commit has its id.");
        }
        subject = Objects.requireNonNullElse(subject, "").strip();
    }

    public String shortId() {
        return id.substring(0, Math.min(id.length(), SHORT_ID));
    }
}
