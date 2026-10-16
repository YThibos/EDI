package io.github.ythibos.edi.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class CapacityTest {

    @TempDir
    private Path directory;

    @Test
    void defersADispatchBeyondTheProjectsCapacityAndLaunchesItWhenAPlaceComesFree() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory).registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    forge: { kind: github, path: example-owner/harness, account: example-owner }
                    base: main
                    capacity: 1
                """);
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4")));
        DispatchOutcome first = dispatch(home, "fit-lamp");

        DispatchOutcome second = dispatch(home, "fit-horn");

        assertThat(second.wasDeferred()).isTrue();
        assertThat(second.deferred().running()).isOne();
        assertThat(home.tasks().get(new TaskId("fit-horn")).state()).isEqualTo(TaskState.QUEUED);
        assertThat(home.launchDeferredDispatches().launchWhereThereIsRoom()).isEmpty();

        WorkerFixtures.opensTheDraftOf(home, first.launched().worker());
        home.recordStatus().report(first.launched().worker().id(), StatusState.DONE, null, null, "The lamp is fitted.");
        List<DispatchOutcome.Launched> launched = home.launchDeferredDispatches().launchWhereThereIsRoom();

        assertThat(launched).extracting(started -> started.worker().id().task()).containsExactly(new TaskId("fit-horn"));
        assertThat(home.tasks().get(new TaskId("fit-horn")).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(home.dispatches().deferred()).isEmpty();
        assertThat(home.eventTypesOf(new TaskId("fit-horn"))).containsExactly("DispatchDeferred", "TaskDispatched");
    }

    @Test
    void withdrawsADeferredDispatchWhoseLaunchFailsAndLaunchesTheNextOne() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory).registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    forge: { kind: github, path: example-owner/harness, account: example-owner }
                    base: main
                    capacity: 1
                    craftRules: true
                  - name: EDI
                    tracker: { kind: edi, projectKey: EDI }
                    forge: { kind: github, path: example-owner/EDI, account: example-owner }
                    base: main
                    capacity: 1
                """);
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4"),
                WorkerFixtures.queuedShip("map-caves", "EDI", "EDI-7"), WorkerFixtures.queuedShip("map-tunnels", "EDI", "EDI-8")));
        DispatchOutcome lamp = dispatch(home, "fit-lamp");
        dispatch(home, "fit-horn");
        DispatchOutcome caves = dispatch(home, "map-caves");
        dispatch(home, "map-tunnels");
        WorkerFixtures.opensTheDraftOf(home, lamp.launched().worker());
        WorkerFixtures.opensTheDraftOf(home, caves.launched().worker());
        home.recordStatus().report(lamp.launched().worker().id(), StatusState.DONE, null, null, "The lamp is fitted.");
        home.recordStatus().report(caves.launched().worker().id(), StatusState.DONE, null, null, "The caves are mapped.");
        Files.delete(home.home().craftRules());

        List<DispatchOutcome.Launched> launched = home.launchDeferredDispatches().launchWhereThereIsRoom();

        assertThat(launched).extracting(started -> started.worker().id().task()).containsExactly(new TaskId("map-tunnels"));
        assertThat(home.dispatches().deferred()).isEmpty();
        assertThat(home.tasks().get(new TaskId("fit-horn")).state()).isEqualTo(TaskState.QUEUED);
        assertThat(home.eventTypesOf(new TaskId("fit-horn"))).containsExactly("DispatchDeferred", "DeferredLaunchFailed");
    }

    private static DispatchOutcome dispatch(TestHome home, String task) {
        return home.dispatchTask().dispatch(TaskReference.parse(task), new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), Actor.PRIMARY);
    }
}
