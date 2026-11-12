package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.CloneRefresh;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.StageReached;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.Stages;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class FollowMergesTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final TaskId HORN = new TaskId("fit-horn");

    @TempDir
    private Path directory;

    private TestHome home;
    private FixtureRepository upstream;

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        upstream = FixtureRepository.at(directory.resolve("upstream/harness"));
    }

    @Test
    void followsADraftTheForgeShowsMergedThroughToACleanedUpTask() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        Delivery delivery = home.deliveries().of(LAMP).orElseThrow();

        home.followMerges().watchTheOpenDrafts();
        assertThat(home.eventTypesOf(LAMP)).doesNotContain("DraftMerged");

        home.forge().merges(delivery.address(), squashedOntoMain(copy));
        home.followMerges().watchTheOpenDrafts();

        assertThat(home.deliveries().of(LAMP).orElseThrow().state()).isEqualTo(MergeRequestState.MERGED);
        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("DraftMerged", "CloneRefreshed", "TaskCostRecorded", "TaskCleanedUp");
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
        assertThat(home.events().stagesOf(LAMP)).extracting(StageReached::stage)
                .containsExactly(TaskStage.WORKING, TaskStage.DELIVERED, TaskStage.MERGED, TaskStage.LANDED, TaskStage.RETIRED);
        assertThat(copy.root()).doesNotExist();
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).contains(WakeKind.DRAFT_MERGED);
    }

    @Test
    void namesACommitAfterTheMergedHeadAsNotLandedAndKeepsTheTaskMerged() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        String mergedHead = copy.head();
        String landed = squashedOntoMain(copy);
        String steered = copy.write("cap.txt", "brass\n").commit("HRN-3 Fit the cap the steer asked for").head();
        home.forge().merges(home.deliveries().of(LAMP).orElseThrow().address(), landed);

        home.followMerges().watchTheOpenDrafts();

        assertThat(home.tasks().get(LAMP).stage()).isEqualTo(TaskStage.MERGED);
        assertThat(home.events().stagesOf(LAMP)).last().extracting(StageReached::reason)
                .isEqualTo("merged at " + mergedHead + "; 1 commit not landed: " + steered.substring(0, 12));
        assertThat(home.jdbc().sql("select json_extract(payload, '$.notLanded[0].subject') from event where type = 'StageChanged' and "
                + "json_extract(payload, '$.to') = 'merged'").query(String.class).single()).isEqualTo("HRN-3 Fit the cap the steer asked for");
        assertThat(home.eventTypesOf(LAMP)).contains("CleanupRefused").doesNotContain("TaskCleanedUp");
    }

    @Test
    void keepsTheHeadAndTheDraftFlagOfAnOpenRequestAfterTheDone() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        Worker worker = DeliveryFixtures.deliveredAsADraft(home, dispatched);
        String pushed = copy.write("cap.txt", "brass\n").commit("HRN-3 Fit the cap").head();
        WorkerFixtures.opensTheDraftOf(home, worker);
        home.forge().marksReady(home.deliveries().of(LAMP).orElseThrow().address());

        home.followMerges().watchTheOpenDrafts();

        Delivery observed = home.deliveries().of(LAMP).orElseThrow();
        assertThat(observed.draft()).isFalse();
        assertThat(observed.headCommit()).isEqualTo(pushed);
        assertThat(home.eventTypesOf(LAMP)).doesNotContain("DraftClosed");
    }

    @Test
    void seesAnOpenRequestLeaveDraftWhenItIsWatchedOrFollowedAfterItsPipelineMoves() throws IOException {
        DispatchOutcome lamp = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(lamp, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, lamp);
        DispatchOutcome horn = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4"));
        DeliveryFixtures.committedIn(horn, "horn.txt", "loud\n", "HRN-4 Fit the horn");
        DeliveryFixtures.deliveredAsADraft(home, horn);
        home.forge().marksReady(home.deliveries().of(LAMP).orElseThrow().address());

        home.followMerges().watchTheOpenDrafts();
        home.forge().marksReady(home.deliveries().of(HORN).orElseThrow().address());
        boolean hornWasADraft = home.deliveries().of(HORN).orElseThrow().draft();
        home.followMerges().followTheRequestOf(HORN);

        assertThat(home.deliveries().of(LAMP).orElseThrow().draft()).isFalse();
        assertThat(hornWasADraft).isTrue();
        assertThat(home.deliveries().of(HORN).orElseThrow().draft()).isFalse();
    }

    @Test
    void followsTheOtherDraftsWhenTheForgeCannotShowOne() throws IOException {
        DispatchOutcome lamp = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(lamp, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, lamp);
        home.clock().advance(Duration.ofMinutes(1));
        DispatchOutcome horn = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4"));
        FixtureRepository hornCopy = DeliveryFixtures.committedIn(horn, "horn.txt", "loud\n", "HRN-4 Fit the horn");
        DeliveryFixtures.deliveredAsADraft(home, horn);
        home.forge().cannotShow(home.deliveries().of(LAMP).orElseThrow().address());
        home.forge().merges(home.deliveries().of(HORN).orElseThrow().address(), squashedOntoMain(hornCopy));

        home.followMerges().watchTheOpenDrafts();

        assertThat(home.deliveries().of(LAMP).orElseThrow().state()).isEqualTo(MergeRequestState.OPEN);
        assertThat(home.eventTypesOf(HORN)).containsSubsequence("DraftMerged", "CloneRefreshed", "TaskCleanedUp");
        assertThat(home.tasks().get(HORN).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void answersTheCaptainsWordFromTheMergeEdiAlreadyFollowed() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        String landed = squashedOntoMain(copy);
        home.forge().merges(home.deliveries().of(LAMP).orElseThrow().address(), landed);
        home.followMerges().watchTheOpenDrafts();

        MergedWork merged = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);

        assertThat(merged.delivery().landedCommit()).isEqualTo(landed);
        assertThat(merged.cleanUp().done()).isTrue();
        assertThat(home.eventTypesOf(LAMP)).containsOnlyOnce("DraftMerged", "CloneRefreshed", "TaskCleanedUp");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsOnlyOnce(WakeKind.DRAFT_MERGED);
        assertThat(home.cloneRefreshes().after(LAMP)).hasValueSatisfying(refresh -> assertThat(refresh.cleanUpTriedAt()).isNotNull());
    }

    @Test
    void triesTheCleanUpAgainWhenTheCaptainSaysAFollowedMergeIsMerged() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        home.forge().merges(home.deliveries().of(LAMP).orElseThrow().address(), squashedOntoMain(copy));
        copy.write("notes.txt", "left behind\n");
        home.followMerges().watchTheOpenDrafts();
        Files.delete(copy.root().resolve("notes.txt"));

        MergedWork merged = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);

        assertThat(merged.cleanUp().done()).isTrue();
        assertThat(home.eventTypesOf(LAMP)).containsOnlyOnce("DraftMerged", "CloneRefreshed", "CleanupRefused", "TaskCleanedUp");
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void triesAgainInALaterRoundACleanUpAFailingProgramStopped() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        home.forge().merges(home.deliveries().of(LAMP).orElseThrow().address(), squashedOntoMain(copy));
        FixtureRepository clone = FixtureRepository.at(home.registry().entryOf(new ProjectName("harness")).projectClone());
        clone.run("worktree", "lock", copy.root().toString());

        MergedWork stopped = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);
        assertThat(stopped.cleanUp().refusal()).startsWith("the clean-up stopped: ").endsWith("; the next round tries again");
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(home.cloneRefreshes().after(LAMP)).hasValueSatisfying(refresh -> assertThat(refresh.cleanUpTriedAt()).isNull());

        clone.run("worktree", "unlock", copy.root().toString());
        home.followMerges().finishEarlierMerges();

        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
        assertThat(copy.root()).doesNotExist();
    }

    @Test
    void refreshesTheOtherClonesWhenOneProjectIsNoLongerRegistered() throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4")));
        home.cloneRefreshStore().save(CloneRefresh.awaiting(HORN, new ProjectName("retired"), new BaseBranch("main"), "1111", home.clock().now()));
        home.clock().advance(Duration.ofMinutes(1));
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        home.forge().merges(home.deliveries().of(LAMP).orElseThrow().address(), squashedOntoMain(copy));

        home.followMerges().watchTheOpenDrafts();

        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("DraftMerged", "CloneRefreshed", "TaskCleanedUp");
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void noticesADraftClosedWithoutAMergeOnce() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        home.forge().closes(home.deliveries().of(LAMP).orElseThrow().address());

        home.followMerges().watchTheOpenDrafts();
        home.followMerges().watchTheOpenDrafts();

        assertThat(home.eventTypesOf(LAMP)).containsOnlyOnce("DraftClosed").doesNotContain("TaskCleanedUp");
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).containsOnlyOnce(WakeKind.DRAFT_CLOSED);
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.IN_FLIGHT);
    }

    @Test
    void takesTheCaptainsWordOnlyWhenTheForgeShowsTheMerge() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        Delivery delivery = home.deliveries().of(LAMP).orElseThrow();

        assertThatThrownBy(() -> home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("The forge shows " + delivery.address() + " open, not merged, so it is still watched.");
        assertThatThrownBy(() -> home.followMerges().mergedSaysTheCaptain("https://github.com/example-owner/harness/pull/99", Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("Nothing was delivered at https://github.com/example-owner/harness/pull/99.");

        String landed = squashedOntoMain(copy);
        home.forge().merges(delivery.address(), landed);
        MergedWork merged = home.followMerges().mergedSaysTheCaptain(delivery.address().url(), Actor.PRIMARY);

        assertThat(merged.delivery().landedCommit()).isEqualTo(landed);
        assertThat(merged.cleanUp().done()).isTrue();
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).doesNotContain(WakeKind.DRAFT_MERGED);
    }

    @Test
    void recordsAnOpenRequestEdiNeverRecordedOnTheCaptainsWordAndKeepsWatchingIt() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        MergeRequestView ready = home.forge().marksReady(WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker()).address());

        assertThatThrownBy(() -> home.followMerges().mergedSaysTheCaptain(ready.address().url(), Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("The forge shows " + ready.address() + " open, not merged, so it is still watched.");
        assertThat(home.deliveries().of(LAMP)).hasValueSatisfying(delivery -> assertThat(delivery.state()).isEqualTo(MergeRequestState.OPEN));
        assertThat(home.eventTypesOf(LAMP)).containsOnlyOnce("DraftRecorded").doesNotContain("DraftMerged");
    }

    @Test
    void followsTheRequestThatSucceedsAClosedOneOnTheCaptainsWordForTheTask() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        home.forge().closes(home.deliveries().of(LAMP).orElseThrow().address());
        home.followMerges().watchTheOpenDrafts();
        MergeRequestView successor = WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker());
        home.forge().merges(successor.address(), squashedOntoMain(copy));

        MergedWork merged = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);

        assertThat(merged.delivery().address()).isEqualTo(successor.address());
        assertThat(merged.cleanUp().done()).isTrue();
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void followsAMergeEdiNeverRecordedPastAnInFlightTaskThatWasNeverDispatched() throws IOException {
        home.storing(List.of(Stages.working(WorkerFixtures.queuedShip("fit-horn", "harness", "HRN-4"))));
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, dispatched.launched().worker());
        home.forge().merges(draft.address(), squashedOntoMain(copy));

        MergedWork merged = home.followMerges().mergedSaysTheCaptain(draft.address().url(), Actor.PRIMARY);

        assertThat(merged.task()).isEqualTo(LAMP);
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void leavesAMergeFromBeforeTheDispatchToTheEarlierTaskOnTheSameBranch() throws IOException {
        RegisteredForge forge = home.registry().entryOf(new ProjectName("harness")).requireForge();
        MergeRequestView earlier = home.forge().opensADraft(forge, BranchName.parse("feature/HRN-3"), new BaseBranch("main"), "an-earlier-head");
        home.forge().merges(earlier.address(), "2222");
        home.clock().advance(Duration.ofMinutes(1));
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));

        home.followMerges().followTheRequestOf(LAMP);

        assertThat(home.deliveries().of(LAMP)).isEmpty();
        assertThatThrownBy(() -> home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessageStartingWith("There is no merge request to follow");
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.IN_FLIGHT);
        assertThat(home.runtime().isRunning(dispatched.launched().worker().window())).isTrue();
    }

    @Test
    void followsAMergeSinceTheDispatchWhoseHeadCarriesThePipelinesFixesBeyondTheCopy() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        RegisteredForge forge = home.registry().entryOf(new ProjectName("harness")).requireForge();
        MergeRequestView fixed = home.forge().opensADraft(forge, dispatched.launched().branch(), new BaseBranch("main"), "a-pipeline-fix-head");
        home.clock().advance(Duration.ofMinutes(1));
        home.forge().merges(fixed.address(), squashedOntoMain(copy));

        home.followMerges().followTheRequestOf(LAMP);

        assertThat(home.deliveries().of(LAMP)).hasValueSatisfying(delivery -> {
            assertThat(delivery.address()).isEqualTo(fixed.address());
            assertThat(delivery.state()).isEqualTo(MergeRequestState.MERGED);
        });
        assertThat(home.eventTypesOf(LAMP)).containsSubsequence("DraftRecorded", "DraftMerged");
    }

    @Test
    void refusesTheCaptainsWordWhenTheForgeShowsNoRequestFromTheTasksBranch() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");

        assertThatThrownBy(() -> home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("There is no merge request to follow: no merge request from feature/HRN-3 is open or merged on"
                        + " https://github.com/example-owner/harness.");
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.IN_FLIGHT);
    }

    @Test
    void wakesThePrimaryOnceWhenTheCleanUpAfterAMergeIsRefused() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        DeliveryFixtures.deliveredAsADraft(home, dispatched);
        home.forge().merges(home.deliveries().of(LAMP).orElseThrow().address(), squashedOntoMain(copy));
        copy.write("notes.txt", "left behind\n");

        home.followMerges().watchTheOpenDrafts();
        home.followMerges().finishEarlierMerges();

        assertThat(home.eventTypesOf(LAMP)).containsOnlyOnce("CleanupRefused").doesNotContain("TaskCleanedUp");
        assertThat(home.wakes().unacknowledged()).filteredOn(queued -> queued.wake().kind() == WakeKind.CLEANUP_REFUSED)
                .singleElement().satisfies(queued -> assertThat(queued.wake().detail()).isEqualTo("notes.txt is not committed"));
        assertThat(copy.root()).isDirectory();
    }

    @Test
    void cleansUpAWorkDeliveredLocallyOnTheCaptainsWordOnceItIsLanded() throws IOException {
        FixtureRepository checkout = upstream.clonedInto(directory.resolve("checkouts/harness"));
        home.registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    base: main
                    captainCheckout: %s
                """.formatted(checkout.root()));
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3", DeliveryMode.LOCAL_BRANCH));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "Ready.");

        MergedWork early = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);
        assertThat(early.cleanUp().done()).isFalse();
        assertThat(early.cleanUp().refusal()).contains("is not in origin/main");

        upstream.run("fetch", "--quiet", copy.root().toString(), "feature/HRN-3");
        upstream.run("merge", "--quiet", "--ff-only", "FETCH_HEAD");
        MergedWork landed = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);

        assertThat(landed.delivery()).isNull();
        assertThat(landed.cleanUp().done()).isTrue();
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
    }

    @Test
    void asksTheCaptainToSayItAgainWhenAFailingProgramStopsALocalCleanUp() throws IOException {
        FixtureRepository checkout = upstream.clonedInto(directory.resolve("checkouts/harness"));
        home.registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    base: main
                    captainCheckout: %s
                """.formatted(checkout.root()));
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3", DeliveryMode.LOCAL_BRANCH));
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "Ready.");
        upstream.run("fetch", "--quiet", copy.root().toString(), "feature/HRN-3");
        upstream.run("merge", "--quiet", "--ff-only", "FETCH_HEAD");
        FixtureRepository.at(home.registry().entryOf(new ProjectName("harness")).projectClone()).run("worktree", "lock", copy.root().toString());

        MergedWork stopped = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);

        assertThat(stopped.cleanUp().done()).isFalse();
        assertThat(stopped.cleanUp().refusal()).startsWith("the clean-up stopped: ")
                .endsWith("; run edi merged or edi cleanup again once the failure clears");
        assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.IN_FLIGHT);
    }

    private String squashedOntoMain(FixtureRepository copy) throws IOException {
        upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
        upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
        return upstream.commit("Fit the lamp, squashed").head();
    }
}
