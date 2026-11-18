package io.github.ythibos.edi.application.task;

import static io.github.ythibos.edi.testing.Intakes.captainItem;
import static io.github.ythibos.edi.testing.Intakes.ship;
import static io.github.ythibos.edi.testing.Intakes.shipBlockedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskSize;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.task.UnknownTask;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.Stages;
import io.github.ythibos.edi.testing.TestHome;

@Tag("adapter")
class AddTaskTest {

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void openAHome() throws IOException {
        home = TestHome.in(directory).registering(Intakes.REGISTRY);
    }

    @Test
    void queuesATaskAndRecordsWhoAddedIt() {
        TaskId id = home.addTask().add(ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY).id();

        assertThat(home.tasks().get(id).state()).isEqualTo(TaskState.QUEUED);
        assertThat(home.tasks().get(id).created()).isEqualTo(TestHome.START);
        assertThat(home.events().timelineOf(id)).extracting(RecordedEvent::type, RecordedEvent::actor)
                .containsExactly(tuple("TaskAdded", Actor.PRIMARY));
    }

    @Test
    void takesTheSizeFromTheSizeRowOfItsBacklogItemWhenGivenNone() {
        Intake intake = ship("unused", "Fit the lamp").intake();
        String item = "| Field | Value |\n|---|---|\n| Kind | ship |\n| Size | S |\n\n## Goal\n\nA lamp.";

        TaskId fromItsItem = home.addTask().add(new NewTask(new TaskId("fit-lamp"), intake, item), Actor.PRIMARY).id();
        TaskId given = home.addTask().add(new NewTask(new TaskId("fit-horn"), intake.sized(TaskSize.L), "Tune it."), Actor.PRIMARY).id();
        TaskId unsized = home.addTask().add(new NewTask(new TaskId("fit-bell"), intake, "Hang it."), Actor.PRIMARY).id();

        assertThat(home.tasks().get(fromItsItem).intake().size()).isEqualTo(TaskSize.S);
        assertThat(home.tasks().get(given).intake().size()).isEqualTo(TaskSize.L);
        assertThat(home.tasks().get(unsized).intake().size()).isNull();
    }

    @Test
    void refusesASizeThatDiffersFromItsBacklogItemsOrThatIsNoSize() {
        Intake intake = ship("unused", "Fit the lamp").intake();

        assertThatThrownBy(() -> home.addTask().add(new NewTask(new TaskId("fit-lamp"), intake.sized(TaskSize.XS), "| Size | M |"), Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("The task is given size XS, but the Size row of its backlog item says M; a task has one size.");
        assertThatThrownBy(() -> home.addTask().add(new NewTask(new TaskId("fit-lamp"), intake, "| Size | huge |"), Actor.PRIMARY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A size is XS, S, M, L, not huge.");
        assertThat(home.tasks().contains(new TaskId("fit-lamp"))).isFalse();
    }

    @Test
    void namesATaskAfterItsTitleWhenNoIdIsChosen() {
        Intake intake = ship("unused", "Fit the lamp!").intake();

        TaskId first = home.addTask().add(new NewTask(null, intake, ""), Actor.PRIMARY).id();
        TaskId second = home.addTask().add(new NewTask(null, intake, ""), Actor.PRIMARY).id();

        assertThat(first).isEqualTo(new TaskId("fit-the-lamp"));
        assertThat(second).isEqualTo(new TaskId("fit-the-lamp-2"));
    }

    @Test
    void neverReusesAnId() {
        home.addTask().add(ship("fit-lamp", "Fit the lamp"), Actor.PRIMARY);

        assertThatThrownBy(() -> home.addTask().add(ship("fit-lamp", "Fit it again"), Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp already exists, and a task id is never reused.");
    }

    @Test
    void takesWorkOnlyForARegisteredProject() {
        NewTask elsewhere = new NewTask(new TaskId("elsewhere"),
                new Intake(TaskKind.SHIP, "Work elsewhere", List.of(new ProjectName("unknown-project")), null, null, List.of(), null, null), "");

        assertThatThrownBy(() -> home.addTask().add(elsewhere, Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("unknown-project is not a registered project; add it to the project registry first.");
    }

    @Test
    void needsTheProjectOfAShipOrScoutButNotOfACaptainItem() {
        NewTask projectless = new NewTask(new TaskId("drift"), new Intake(TaskKind.SCOUT, "Look around", List.of(), null, null, List.of(), null, null), "");

        assertThatThrownBy(() -> home.addTask().add(projectless, Actor.PRIMARY)).isInstanceOf(Refusal.class).hasMessage("A scout names the project it works on.");
        assertThat(home.addTask().add(captainItem("lamp-choice", "Choose the lamp"), Actor.PRIMARY).id()).isEqualTo(new TaskId("lamp-choice"));
    }

    @Test
    void waitsOnlyForTasksItKnows() {
        assertThatThrownBy(() -> home.addTask().add(shipBlockedBy("fog-horn", "Tune the horn", "lantern"), Actor.PRIMARY))
                .isInstanceOf(UnknownTask.class);
        assertThat(home.tasks().contains(new TaskId("fog-horn"))).isFalse();
    }

    @Test
    void neverWaitsForAClosedTask() {
        TaskId lantern = home.addTask().add(ship("lantern", "Light the lantern"), Actor.PRIMARY).id();
        home.tasks().update(Stages.retired(home.tasks().get(lantern), TestHome.START));

        assertThatThrownBy(() -> home.addTask().add(shipBlockedBy("fog-horn", "Tune the horn", "lantern"), Actor.PRIMARY))
                .isInstanceOf(Refusal.class)
                .hasMessage("lantern is already done, so no task can wait for it.");
        assertThat(home.tasks().contains(new TaskId("fog-horn"))).isFalse();
    }
}
