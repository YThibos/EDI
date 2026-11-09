package io.github.ythibos.edi.application.migration;

import static io.github.ythibos.edi.testing.Intakes.ship;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.Stages;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class BacklogImportTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void openAHome() throws IOException {
        home = TestHome.in(directory).registering(Intakes.REGISTRY);
    }

    @Test
    void keepsOnlyTheBlockersThatAreStillOpenInEdi() {
        TaskId lantern = home.addTask().add(ship("lantern", "Light the lantern"), Actor.PRIMARY).id();
        home.tasks().update(Stages.retired(home.tasks().get(lantern), TestHome.START));
        TaskId mast = home.addTask().add(ship("mast", "Raise the mast"), Actor.PRIMARY).id();

        home.backlogImport().importInto(List.of(new MigratedEntry(oldTask("fog-horn", "lantern", "mast", "elsewhere"), null)),
                directory.resolve("old-home"), Actor.PRIMARY);

        assertThat(home.tasks().get(new TaskId("fog-horn")).intake().blockedBy()).containsExactly(mast);
    }

    private static Task oldTask(String id, String... blockers) {
        return Task.queued(new TaskId(id), Intakes.shipBlockedBy(id, "Tune the horn", blockers).intake(), "", TestHome.START);
    }
}
