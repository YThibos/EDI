package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.FakeBoardHost;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.Intakes;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class CleanUpTaskTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;
    private FixtureRepository upstream;
    private FixtureRepository clone;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        upstream = FixtureRepository.at(directory.resolve("upstream/harness"));
        clone = FixtureRepository.at(home.home().root().resolve("projects/harness"));
    }

    @Test
    void waitsWhileTheWorkerStillWorks() {
        DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));

        assertThatThrownBy(() -> cleanUp("fit-lamp")).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp#1 still works on fit-lamp;"
                        + " it is cleaned up once its done is accepted or the forge shows its merge request merged.");
    }

    @Test
    void takesAMergeTheForgeShowsAsTheHandOverOfAWorkerThatStillRuns() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker());
        upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
        upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
        MergeRequestView merged = home.forge().merges(draft.address(), upstream.commit("Fit the lamp, squashed").head());
        home.deliveries().save(Delivery.of(LAMP, merged, home.clock().now()));

        cleanUp("fit-lamp");

        assertThat(home.runtime().isRunning(dispatched.launched().worker().window())).isFalse();
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void refusesUnlandedWorkAndLeavesEverythingAsItWas() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);

        assertThatThrownBy(() -> cleanUp("fit-lamp")).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp is not cleaned up, because its work is not landed: commit " + copy.head().substring(0, 12)
                        + " \"HRN-3 Fit the lamp\" is not in origin/main. Nothing is discarded without the captain's word.");
        assertThat(copy.root()).isDirectory();
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(home.eventTypesOf(LAMP)).endsWith("CleanupRefused");
    }

    @Test
    void retiresTheCopyTheBranchAndTheWorkersOfLandedWorkAndCountsItsCost() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        Worker worker = dispatched.launched().worker();
        home.workerHooks().sessionStarted(worker.id(), worker.session(), directory.resolve("transcript.jsonl").toString());
        home.runtime().transcriptSpent(new TokenUsage(10, 200, 30, 4));
        home.pipeline().spent(new TokenUsage(1, 2, 3, 4));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
        upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
        upstream.commit("Fit the lamp, squashed");

        cleanUp("fit-lamp");

        assertThat(copy.root()).doesNotExist();
        assertThat(clone.run("branch", "--list", "feature/HRN-3")).isBlank();
        assertThat(home.home().workerState(worker.id())).doesNotExist();
        assertThat(home.workers().get(worker.id()).orElseThrow().isRetired()).isTrue();
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
        assertThat(home.taskCosts().of(LAMP)).hasValueSatisfying(cost -> {
            assertThat(cost.workers()).isEqualTo(new TokenUsage(10, 200, 30, 4));
            assertThat(cost.pipelineIfKnown()).contains(new TokenUsage(1, 2, 3, 4));
        });
        assertThat(home.eventTypesOf(LAMP)).endsWith("WorkerRetired", "TaskCostRecorded", "ReviewCounted", "TaskCleanedUp");
    }

    @Test
    void stopsEveryOtherTaskWaitingForTheTaskItCleansUp() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        home.addTask().add(Intakes.shipBlockedBy("paint-lamp", "Paint the lamp", "fit-lamp"), Actor.PRIMARY);
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
        upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
        upstream.commit("Fit the lamp, squashed");

        cleanUp("fit-lamp");

        assertThat(home.tasks().get(new TaskId("paint-lamp")).intake().blockedBy()).isEmpty();
    }

    @Test
    void refusesACopyThatHoldsANestedRepository() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        FixtureRepository.initialise(Files.createDirectories(dispatched.launched().worktree().resolve("vendor/tool"))).write("a.txt", "a\n").commit("Start");

        assertThatThrownBy(() -> cleanUp("fit-lamp")).isInstanceOf(Refusal.class)
                .hasMessageContaining("vendor/tool/ is a repository nested in the copy, and nothing of it is landed");
    }

    @Test
    void cleansUpAScoutOnlyOnceItsReportExistsAndRemovesItsNestedRepositories() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queued("map-caves", TaskKind.SCOUT, "harness", "HRN-5"));
        Worker scout = dispatched.launched().worker();
        Path nested = Files.createDirectories(dispatched.launched().worktree().resolve("scratch/upstream-copy"));
        FixtureRepository.initialise(nested).write("a.txt", "a\n").commit("Start");
        Path report = Files.writeString(home.home().taskDirectory(new TaskId("map-caves")).resolve("report.md"), "The caves run north.\n");
        home.recordStatus().report(scout.id(), StatusState.DONE, null, null, "The caves are mapped.");
        Files.delete(report);

        assertThatThrownBy(() -> cleanUp("map-caves")).isInstanceOf(Refusal.class)
                .hasMessageContaining("a scout is cleaned up only after its done was accepted and its report");

        Files.writeString(report, "The caves run north.\n");
        cleanUp("map-caves");

        assertThat(dispatched.launched().worktree()).doesNotExist();
        assertThat(home.tasks().get(new TaskId("map-caves")).state()).isEqualTo(TaskState.DONE);
        assertThat(home.taskCosts().of(new TaskId("map-caves"))).map(TaskCost::workers).contains(TokenUsage.NONE);
    }

    @Test
    void retiresTheResearchBoardOfACleanedUpScoutAndStopsItsListener() throws IOException, InterruptedException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queued("map-caves", TaskKind.SCOUT, "harness", "HRN-5"));
        Worker scout = dispatched.launched().worker();
        Path taskDirectory = home.home().taskDirectory(new TaskId("map-caves"));
        Path board = Files.writeString(Files.createDirectories(taskDirectory).resolve("board.html"), "<!doctype html>\n");
        home.boardHost().holdsTheNextPollOpen();
        CountDownLatch polled = home.boardHost().expectPolls(1);
        home.armBoard().arm(scout.id(), TaskReference.parse("map-caves"), board, BoardRounds.binding());
        FakeBoardHost.await(polled);
        Files.writeString(taskDirectory.resolve("report.md"), "The caves run north.\n");
        home.recordStatus().report(scout.id(), StatusState.DONE, null, null, "The caves are mapped.");

        cleanUp("map-caves");

        home.boardHost().awaitStopOfTheHeldPoll();
        assertThat(home.boards().listening()).isEmpty();
        assertThat(home.eventTypesOf(new TaskId("map-caves"))).contains("BoardRetired");
    }

    private void cleanUp(String task) {
        home.cleanUpTask().cleanUp(TaskReference.parse(task), Actor.PRIMARY);
    }
}
