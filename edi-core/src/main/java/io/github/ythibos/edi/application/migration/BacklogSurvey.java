package io.github.ythibos.edi.application.migration;

import java.util.List;

import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record BacklogSurvey(
        List<TaskId> alreadyInEdi,
        List<TaskId> ediOwnTasks,
        List<DecisionNumber> ediOwnDecisions,
        List<MigrationNote> conflicts,
        int lastDecisionNumber,
        WorkKey upcomingEdiKey) {

    public BacklogSurvey {
        alreadyInEdi = List.copyOf(alreadyInEdi);
        ediOwnTasks = List.copyOf(ediOwnTasks);
        ediOwnDecisions = List.copyOf(ediOwnDecisions);
        conflicts = List.copyOf(conflicts);
    }

    public boolean findsWorkOfEdisOwn() {
        return !ediOwnTasks.isEmpty() || !ediOwnDecisions.isEmpty();
    }
}
