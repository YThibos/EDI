package io.github.ythibos.edi.web.task;

import java.nio.file.Path;
import java.util.UUID;

import io.github.ythibos.edi.domain.branch.BranchName;

record AdoptRequest(String from, String branch, String worktree, String session) {

    Path oldHome() {
        return from == null || from.isBlank() ? null : absolute(from, "the old home");
    }

    BranchName branchName() {
        return BranchName.parse(branch == null ? "" : branch);
    }

    Path oldCopy() {
        return absolute(worktree, "the old isolated copy");
    }

    UUID sessionToResume() {
        if (session == null || session.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(session.strip());
        } catch (IllegalArgumentException notASession) {
            throw new IllegalArgumentException("A Claude Code session is named by its UUID, not " + session + ".", notASession);
        }
    }

    private static Path absolute(String path, String what) {
        Path named = Path.of(path == null ? "" : path);
        if (!named.isAbsolute()) {
            throw new IllegalArgumentException("Name " + what + " by its absolute path, not " + path + ".");
        }
        return named.normalize();
    }
}
