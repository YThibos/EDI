package io.github.ythibos.edi.application.supervision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.recovery.OutageEntry;
import io.github.ythibos.edi.domain.task.StageChange;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class OutageHoldListTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker lamp;

    @BeforeEach
    void prepareThreeWorkers() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
        WorkerFixtures.dispatchedShip(home, "tune-horn", "HRN-4");
        WorkerFixtures.dispatchedShip(home, "ring-bell", "HRN-5");
    }

    @Test
    void anEntryWhoseTaskMergedBeforeItCouldLeaveIsLetGoTheNextTimeTheListIsBuilt() {
        home.runtime().restartTheServer();
        home.outageHoldList().holdTheDead();
        home.tasks().update(mergedWithoutLeavingTheList(home.tasks().get(lamp.id().task())));

        List<OutageEntry> listed = home.outageHoldList().holdTheDead();

        assertThat(listed).extracting(entry -> entry.number() + " " + entry.task()).containsExactly("2 tune-horn", "3 ring-bell");
        assertThat(home.tasks().get(lamp.id().task()).isHeld()).isFalse();
        assertThat(home.eventTypesOf(lamp.id().task())).endsWith("TaskReleased");
    }

    @Test
    void aDeadWorkerWhoseTaskAlreadyMergedIsNotListed() {
        home.tasks().update(mergedWithoutLeavingTheList(home.tasks().get(lamp.id().task())));
        home.runtime().restartTheServer();

        List<OutageEntry> listed = home.outageHoldList().holdTheDead();

        assertThat(listed).extracting(entry -> entry.number() + " " + entry.task()).containsExactly("1 tune-horn", "2 ring-bell");
        assertThat(home.tasks().get(lamp.id().task()).isHeld()).isFalse();
        assertThat(home.eventTypesOf(lamp.id().task())).doesNotContain("LostInOutage", "TaskHeld");
    }

    private Task mergedWithoutLeavingTheList(Task task) {
        TaskId id = task.id();
        return task.movedBy(new StageChange(id, TaskStage.WORKING, TaskStage.MERGED, "merged at 1a2b3c4", List.of()), home.clock().now());
    }
}
