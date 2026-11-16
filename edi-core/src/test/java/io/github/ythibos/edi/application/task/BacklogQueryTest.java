package io.github.ythibos.edi.application.task;

import static io.github.ythibos.edi.testing.Intakes.ship;
import static io.github.ythibos.edi.testing.Intakes.shipBlockedBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.task.Wait;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class BacklogQueryTest {

    @TempDir
    private Path directory;

    @Test
    void listsTheQueueInOrderWithWhatHoldsOrBlocksEachTask() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(ship("lantern", "Rework the lantern"), Actor.PRIMARY);
        home.addTask().add(shipBlockedBy("fog-horn", "Tune the horn", "lantern"), Actor.PRIMARY);
        home.addTask().add(ship("flag-pole", "Replace the pole"), Actor.PRIMARY);
        home.holdTask().hold(new TaskReference.ById(new TaskId("flag-pole")), HoldKind.APPROVAL, "needs a yes", HoldUntil.captainsWord(), Actor.PRIMARY);

        Backlog backlog = home.backlog().backlog(false);
        Backlog ready = home.backlog().backlog(true);

        assertThat(backlog.entries()).extracting(entry -> entry.task().id().value()).containsExactly("lantern", "fog-horn", "flag-pole");
        assertThat(backlog.entries().get(1).waits()).extracting(Wait::stage).containsExactly(TaskStage.QUEUED);
        assertThat(ready.readyOnly()).isTrue();
        assertThat(ready.entries()).extracting(entry -> entry.task().id().value()).containsExactly("lantern");
    }

    @Test
    void listsOpenWorkOrOneState() throws IOException {
        TestHome home = TestHome.in(directory).registering(Intakes.REGISTRY);
        home.addTask().add(ship("lantern", "Rework the lantern"), Actor.PRIMARY);

        assertThat(home.taskList().openWork().states()).containsExactly(TaskState.IN_FLIGHT, TaskState.QUEUED);
        assertThat(home.taskList().openWork().tasks()).hasSize(1);
        assertThat(home.taskList().inState(TaskState.DONE).tasks()).isEmpty();
    }
}
