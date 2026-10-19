package io.github.ythibos.edi.application.migration;

import java.nio.file.Path;
import java.util.List;

import io.github.ythibos.edi.domain.decision.Decision;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Task;

public record MigrationPlan(
        Path source,
        List<Task> tasks,
        List<Decision> decisions,
        DecisionSequenceSeed decisionSequence,
        WorkKey highestEdiKey,
        List<SetAsideSection> setAside,
        List<String> oldHarnessWork,
        HomeImport home,
        List<MigrationNote> notes) {

    public MigrationPlan {
        tasks = List.copyOf(tasks);
        decisions = List.copyOf(decisions);
        setAside = List.copyOf(setAside);
        oldHarnessWork = List.copyOf(oldHarnessWork);
        notes = List.copyOf(notes);
    }
}
