package io.github.ythibos.edi.testing;

import java.util.List;

import io.github.ythibos.edi.application.task.NewTask;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;

public final class Intakes {

    public static final String REGISTRY = """
            projects:
              - name: harness
                tracker: { kind: edi, projectKey: HRN }
              - name: garden
            """;

    private Intakes() {
    }

    public static NewTask ship(String id, String title) {
        return new NewTask(new TaskId(id), new Intake(TaskKind.SHIP, title, List.of(new ProjectName("harness")), null, null, List.of(), null), "");
    }

    public static NewTask captainItem(String id, String title) {
        return new NewTask(new TaskId(id), new Intake(TaskKind.CAPTAIN, title, List.of(), null, null, List.of(), null), "");
    }

    public static NewTask shipBlockedBy(String id, String title, String blocker) {
        return new NewTask(new TaskId(id), new Intake(TaskKind.SHIP, title, List.of(new ProjectName("harness")), null, null, List.of(new TaskId(blocker)), null), "");
    }
}
