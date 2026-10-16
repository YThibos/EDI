package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class LocalDeliveryTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;
    private FixtureRepository upstream;
    private FixtureRepository checkout;

    @BeforeEach
    void createTheHomeAndTheCaptainsCheckout() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        upstream = FixtureRepository.at(directory.resolve("upstream/harness"));
        upstream.write("lamp.txt", "a lamp\n").commit("Add the lamp");
        checkout = upstream.clonedInto(directory.resolve("checkouts/harness"));
        home.registering("""
                projects:
                  - name: harness
                    tracker: { kind: edi, projectKey: HRN }
                    base: main
                    captainCheckout: %s
                """.formatted(checkout.root()));
    }

    @Test
    void appliesTheChangeToTheCaptainsWorkingTreeWithoutCommittingIt() throws IOException {
        DispatchOutcome dispatched = localApply();
        FixtureRepository copy = FixtureRepository.at(dispatched.launched().worktree())
                .write("lamp.txt", "a brass lamp\n").write("horn.txt", "a horn\n").commit("HRN-3 Fit the lamp and the horn");
        String headBefore = checkout.head();

        done(dispatched);

        assertThat(Files.readString(checkout.root().resolve("lamp.txt"))).isEqualTo("a brass lamp\n");
        assertThat(Files.readString(checkout.root().resolve("horn.txt"))).isEqualTo("a horn\n");
        assertThat(checkout.head()).isEqualTo(headBefore);
        assertThat(checkout.run("status", "--porcelain")).isEqualTo(" M lamp.txt\n?? horn.txt\n");
        assertThat(home.home().taskDirectory(LAMP).resolve("change.patch")).isRegularFile();
        assertThat(home.eventTypesOf(LAMP)).endsWith("ChangeAppliedToCaptain");
        assertThat(copy.head()).isNotEqualTo(headBefore);
    }

    @Test
    void appliesOnlyTheChangeTheBaseDoesNotHoldYet() throws IOException {
        DispatchOutcome dispatched = localApply();
        FixtureRepository copy = FixtureRepository.at(dispatched.launched().worktree());
        copy.write("lamp.txt", "a brass lamp\n").commit("HRN-3 Fit the brass lamp");
        upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
        upstream.run("cherry-pick", "FETCH_HEAD");
        checkout.run("pull", "--quiet", "--ff-only");
        copy.write("horn.txt", "a horn\n").commit("HRN-3 Fit the horn");

        done(dispatched);

        assertThat(home.eventTypesOf(LAMP)).endsWith("ChangeAppliedToCaptain");
        assertThat(checkout.run("status", "--porcelain")).isEqualTo("?? horn.txt\n");
        assertThat(Files.readString(home.home().taskDirectory(LAMP).resolve("change.patch"))).contains("horn.txt").doesNotContain("brass");
    }

    @Test
    void holdsADeletionForTheCaptainsOwnLook() throws IOException {
        DispatchOutcome dispatched = localApply();
        FixtureRepository.at(dispatched.launched().worktree()).remove("lamp.txt").commit("HRN-3 Remove the lamp");

        done(dispatched);

        assertThat(checkout.root().resolve("lamp.txt")).exists();
        assertThat(checkout.run("status", "--porcelain")).isEmpty();
        assertThat(home.eventTypesOf(LAMP)).endsWith("ChangeNotApplied");
        assertThat(home.wakes().unacknowledged()).filteredOn(queued -> queued.wake().kind() == WakeKind.DELIVERY_FAILED).singleElement()
                .satisfies(queued -> assertThat(queued.wake().detail()).startsWith("the change deleted lamp.txt, which waits for the captain's own look")
                        .endsWith(home.home().taskDirectory(LAMP).resolve("change.patch").toString()));
    }

    @Test
    void leavesTheCaptainsUncommittedWorkAlone() throws IOException {
        DispatchOutcome dispatched = localApply();
        FixtureRepository.at(dispatched.launched().worktree()).write("lamp.txt", "a brass lamp\n").commit("HRN-3 Fit the brass lamp");
        checkout.write("lamp.txt", "the captain's own lamp\n");

        done(dispatched);

        assertThat(Files.readString(checkout.root().resolve("lamp.txt"))).isEqualTo("the captain's own lamp\n");
        assertThat(home.wakes().unacknowledged()).filteredOn(queued -> queued.wake().kind() == WakeKind.DELIVERY_FAILED).singleElement()
                .satisfies(queued -> assertThat(queued.wake().detail()).startsWith("the captain's checkout has uncommitted work in lamp.txt"));
    }

    private DispatchOutcome localApply() {
        return DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3", DeliveryMode.LOCAL_APPLY));
    }

    private void done(DispatchOutcome dispatched) {
        home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "Ready.");
    }
}
