package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import io.github.ythibos.edi.application.task.NewTask;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.tracker.TicketDetails;

public final class TrackerFixtures {

    public static final String REGISTRY = """
            projects:
              - name: service
                tracker: { kind: jira, projectKey: SVC }
                base: main
              - name: harness
                tracker: { kind: edi, projectKey: HRN }
                base: main
              - name: garden
                base: main
            """;

    private TrackerFixtures() {
    }

    public static TestHome laptopHome(Path directory) throws IOException {
        TestHome home = TestHome.in(directory).on(MachineFixture.LAPTOP).registering(REGISTRY);
        home.jira().holding("SVC-1", "Project coordination and support", "In Progress")
                .holding("SVC-2", "Tooling and CI/CD", "In Progress")
                .holding("SVC-40", "Harbour lamps", "To Do");
        return home;
    }

    public static NewTask ship(String id, String project, String title, String key) {
        return ship(id, project, title, key, TicketDetails.NONE);
    }

    public static NewTask ship(String id, String project, String title, String key, TicketDetails details) {
        Intake intake = new Intake(TaskKind.SHIP, title, List.of(new ProjectName(project)), key == null ? null : WorkKey.parse(key), null, List.of(),
                null);
        return new NewTask(new TaskId(id), intake, "Do " + title.toLowerCase(Locale.ROOT) + ".", details);
    }
}
