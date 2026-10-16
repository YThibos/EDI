package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.testing.FixtureRepository;
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
        assertThat(copy.root()).doesNotExist();
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).contains(WakeKind.DRAFT_MERGED);
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
                .hasMessage("The forge shows " + delivery.address() + " open, not merged, so EDI keeps watching it.");
        assertThatThrownBy(() -> home.followMerges().mergedSaysTheCaptain("https://github.com/example-owner/harness/pull/99", Actor.PRIMARY))
                .isInstanceOf(Refusal.class).hasMessage("EDI delivered nothing at https://github.com/example-owner/harness/pull/99.");

        String landed = squashedOntoMain(copy);
        home.forge().merges(delivery.address(), landed);
        MergedWork merged = home.followMerges().mergedSaysTheCaptain(delivery.address().url(), Actor.PRIMARY);

        assertThat(merged.delivery().landedCommit()).isEqualTo(landed);
        assertThat(merged.cleanUp().done()).isTrue();
        assertThat(home.wakes().unacknowledged()).extracting(queued -> queued.wake().kind()).doesNotContain(WakeKind.DRAFT_MERGED);
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

    private String squashedOntoMain(FixtureRepository copy) throws IOException {
        upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
        upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
        return upstream.commit("Fit the lamp, squashed").head();
    }
}
