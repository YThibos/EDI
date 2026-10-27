package io.github.ythibos.edi.domain.task;

import java.util.List;

import io.github.ythibos.edi.domain.landing.BranchCommit;

public record StageChange(TaskId task, TaskStage from, TaskStage to, String reason, List<BranchCommit> notLanded) {

    public StageChange {
        notLanded = List.copyOf(notLanded);
    }

    public boolean raisesAFollowUpDecision() {
        return from == TaskStage.CHANGING && to == TaskStage.MERGED && !notLanded.isEmpty();
    }
}
