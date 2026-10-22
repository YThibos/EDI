package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class MechanicalChangeTest {

    private static final TaskId TASK = new TaskId("rename-lamp");

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void prepareAHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("rename-lamp", "harness", "HRN-6")));
    }

    @Test
    void refusesAMechanicalDispatchWithoutItsExactChangeOrOnAProtectedFile() {
        assertThatThrownBy(() -> dispatch("", List.of("src/Lamp.java"))).isInstanceOf(Refusal.class).hasMessageContaining("states its exact expected change");
        assertThatThrownBy(() -> dispatch("Rename Lamp to Lantern.", List.of())).isInstanceOf(Refusal.class).hasMessageContaining("names every file");
        assertThatThrownBy(() -> dispatch("Fix a typo.", List.of("docs/decisions/ADR-007-six-ports.md")))
                .isInstanceOf(Refusal.class).hasMessageContaining("never edits a decision record");
        assertThatThrownBy(() -> dispatch("Fix a typo.", List.of("src/main/resources/db/migration/V2__workers.sql")))
                .isInstanceOf(Refusal.class).hasMessageContaining("never edits a migration");
        assertThatThrownBy(() -> dispatch("Fix a typo.", List.of("src/test/java/LampTest.java")))
                .isInstanceOf(Refusal.class).hasMessageContaining("a test or its expected values");
    }

    @Test
    void acceptsADoneThatTouchesOnlyTheNamedFilesAndPassesTheVerifyCommand() throws IOException {
        Worker worker = mechanicalWorkerThatChanged("src/Lamp.java");

        home.checkMechanicalChange().check(worker.id());

        assertThat(home.workers().get(worker.id()).orElseThrow().doneCheck()).isEqualTo(DoneCheck.ACCEPTED);
        assertThat(home.eventTypesOf(TASK)).endsWith("StatusReported", "DraftRecorded", "MechanicalChangeAccepted").containsOnlyOnce("DraftRecorded");
        assertThat(home.runtime().launches()).hasSize(1);
    }

    @Test
    void handsAChangeBeyondTheNamedFilesToAnImplementationWorker() throws IOException {
        Worker worker = mechanicalWorkerThatChanged("src/Lamp.java", "src/Horn.java");

        home.checkMechanicalChange().check(worker.id());

        Worker handedOver = home.workers().get(new WorkerId(TASK, 2)).orElseThrow();
        assertThat(home.workers().get(worker.id()).orElseThrow().doneCheck()).isEqualTo(DoneCheck.REFUSED);
        assertThat(home.workers().get(worker.id()).orElseThrow().isRetired()).isTrue();
        assertThat(handedOver.category()).isEqualTo(CategoryId.IMPLEMENTATION);
        assertThat(handedOver.route().model()).isEqualTo(new ModelId("claude-sonnet-5-5"));
        assertThat(home.eventTypesOf(TASK)).containsSubsequence("MechanicalChangeRefused", "WorkerRetired", "TaskDispatched");
        assertThat(Files.readString(home.home().taskDirectory(TASK).resolve("brief.md")))
                .contains("A mechanical worker's done was refused: the change touches [src/Horn.java], which the brief did not name.");
    }

    @Test
    void handsAChangeWhoseVerifyFailsToAnImplementationWorker() throws IOException {
        home.registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    forge: { kind: github, path: example-owner/harness, account: example-owner }
                    base: main
                    verify: "echo the build broke; exit 1"
                """);
        Worker worker = mechanicalWorkerThatChanged("src/Lamp.java");

        home.checkMechanicalChange().check(worker.id());

        assertThat(home.workers().get(worker.id()).orElseThrow().doneCheck()).isEqualTo(DoneCheck.REFUSED);
        assertThat(Files.readString(home.home().taskDirectory(TASK).resolve("mechanical-verify.log"))).contains("the build broke");
        assertThat(home.workers().get(new WorkerId(TASK, 2))).isPresent();
    }

    @Test
    void handsADoneWithoutTheNamedChangeToAnImplementationWorker() throws IOException {
        Worker worker = doneReportedBy(mechanicalDispatch());

        home.checkMechanicalChange().check(worker.id());

        assertThat(home.workers().get(worker.id()).orElseThrow().doneCheck()).isEqualTo(DoneCheck.REFUSED);
        assertThat(home.workers().get(new WorkerId(TASK, 2)).orElseThrow().category()).isEqualTo(CategoryId.IMPLEMENTATION);
        assertThat(home.eventTypesOf(TASK)).containsSubsequence("MechanicalChangeRefused", "WorkerRetired", "TaskDispatched");
        assertThat(Files.readString(home.home().taskDirectory(TASK).resolve("brief.md")))
                .contains("A mechanical worker's done was refused: the change touches none of [src/Lamp.java], the files the brief named.");
    }

    private Worker mechanicalWorkerThatChanged(String... files) throws IOException {
        DispatchOutcome dispatched = mechanicalDispatch();
        FixtureRepository worktree = FixtureRepository.at(dispatched.launched().worktree());
        for (String file : files) {
            worktree.write(file, "class Lantern {}\n");
        }
        worktree.commit("HRN-6 Rename Lamp to Lantern");
        return doneReportedBy(dispatched);
    }

    private DispatchOutcome mechanicalDispatch() {
        return dispatch("Rename Lamp to Lantern in src/Lamp.java.", List.of("src/Lamp.java"));
    }

    private Worker doneReportedBy(DispatchOutcome dispatched) throws IOException {
        WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker());
        Worker reported = home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "Renamed.");
        assertThat(reported.doneCheck()).isEqualTo(DoneCheck.CHECKING);
        return reported;
    }

    private DispatchOutcome dispatch(String spec, List<String> files) {
        return home.dispatchTask().dispatch(TaskReference.parse(TASK.value()), new DispatchRequest(CategoryId.MECHANICAL, spec, files, null), Actor.PRIMARY);
    }
}
