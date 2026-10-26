package io.github.ythibos.edi.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class RewriteBriefTest {

    private static final String SPEC = "Fit the lamp; the hall switch also dims it, with a test for the dimmer.";
    private static final String SCOPE_NOTE = "Scope addition: the porch lamp gets the same dimmer, with a test.";

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHomeWithProjects() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void givesThePipelineEveryRequirementOfTheSpecSoItsReviewNeverTakesOneForScopeCreep() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));

        dispatch("fit-lamp");

        assertThat(intentOf("fit-lamp")).contains("## Captain's intent\n\nDo the work on fit-lamp.", "## Spec\n\n" + SPEC);
    }

    @Test
    void carriesAScopeNoteAddedAfterTheDispatchIntoTheIntentAndTheBrief() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        dispatch("fit-lamp");

        home.noteTask().note(TaskReference.parse("fit-lamp"), SCOPE_NOTE, Actor.PRIMARY);

        assertThat(intentOf("fit-lamp")).contains("Do the work on fit-lamp.\n\n" + SCOPE_NOTE + "\n\n## Spec\n\n" + SPEC);
        assertThat(Files.readString(taskDirectory("fit-lamp").resolve("brief.md"))).contains("## Captain's intent\n\nDo the work on fit-lamp.\n\n"
                + SCOPE_NOTE + "\n\n## Spec");
    }

    @Test
    void writesNoBriefForATaskNotYetDispatched() {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));

        home.noteTask().note(TaskReference.parse("fit-lamp"), SCOPE_NOTE, Actor.PRIMARY);

        assertThat(taskDirectory("fit-lamp").resolve("brief.md")).doesNotExist();
        assertThat(taskDirectory("fit-lamp").resolve("intent.md")).doesNotExist();
    }

    @Test
    void leavesTheBriefOfADoneTaskAsItWasWritten() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        dispatch("fit-lamp");
        String written = intentOf("fit-lamp");
        home.markDone(new TaskId("fit-lamp"));

        home.noteTask().note(TaskReference.parse("fit-lamp"), SCOPE_NOTE, Actor.PRIMARY);

        assertThat(intentOf("fit-lamp")).isEqualTo(written);
    }

    private void dispatch(String task) {
        home.dispatchTask().dispatch(TaskReference.parse(task), new DispatchRequest(CategoryId.IMPLEMENTATION, SPEC, List.of(), null),
                CallingSession.UNNAMED, Actor.PRIMARY);
    }

    private String intentOf(String task) throws IOException {
        return Files.readString(taskDirectory(task).resolve("intent.md"));
    }

    private Path taskDirectory(String task) {
        return home.home().taskDirectory(new TaskId(task));
    }
}
