package io.github.ythibos.edi.application.migration;

import java.nio.file.Path;
import java.util.UUID;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.task.TaskId;

public record AdoptedTask(TaskId task, BranchName branch, Path copy, String head, Path oldCopy, UUID session) {

    public boolean resumesASession() {
        return session != null;
    }
}
