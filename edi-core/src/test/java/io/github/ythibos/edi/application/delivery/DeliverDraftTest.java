package io.github.ythibos.edi.application.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.delivery.DeliverDraft.DeliveredDraft;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class DeliverDraftTest {

    private static final TaskReference LAMP = TaskReference.parse("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;
    private RegisteredForge forge;

    @BeforeAll
    static void requireGitleaks() {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
    }

    @BeforeEach
    void createTheHome() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
        forge = home.registry().entryOf(new ProjectName("harness")).requireForge();
    }

    @Test
    void opensTheDraftTitledByTheKeyWithTheDeliveryNoteAsItsBody() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = pushed(dispatched);
        Files.writeString(home.home().taskDirectory(new TaskId("fit-lamp")).resolve("report.md"), "Fits the brass lamp in the hall.\n");
        home.forge().pushed(dispatched.launched().branch(), copy.head());

        DeliveredDraft delivered = home.deliverDraft().deliver(LAMP, Actor.PRIMARY);

        assertThat(delivered.opened()).isTrue();
        assertThat(delivered.delivery().draft()).isTrue();
        assertThat(home.forge().openedDrafts()).singleElement().satisfies(proposal -> {
            assertThat(proposal.title()).isEqualTo("HRN-3 Work on fit-lamp");
            assertThat(proposal.description()).isEqualTo("Fits the brass lamp in the hall.");
            assertThat(proposal.base()).isEqualTo(new BaseBranch("main"));
        });
        assertThat(home.deliveries().of(new TaskId("fit-lamp"))).contains(delivered.delivery());
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("DraftOpened");
    }

    @Test
    void recordsTheDraftThePipelineAlreadyOpened() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = pushed(dispatched);
        home.forge().opensADraft(forge, dispatched.launched().branch(), new BaseBranch("main"), copy.head());

        DeliveredDraft delivered = home.deliverDraft().deliver(LAMP, Actor.PRIMARY);

        assertThat(delivered.opened()).isFalse();
        assertThat(home.forge().openedDrafts()).isEmpty();
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("DraftRecorded");
    }

    @Test
    void recordsAMergeRequestAlreadyReadyForReview() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = pushed(dispatched);
        home.forge().opensAReadyMergeRequest(forge, dispatched.launched().branch(), new BaseBranch("main"), copy.head());

        DeliveredDraft delivered = home.deliverDraft().deliver(LAMP, Actor.PRIMARY);

        assertThat(delivered.opened()).isFalse();
        assertThat(delivered.delivery().draft()).isFalse();
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("DraftRecorded");
    }

    @Test
    void refusesAMergeRequestNotAtTheCopysHeadOrAlreadyMerged() throws IOException {
        DispatchOutcome dispatched = DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"));
        FixtureRepository copy = pushed(dispatched);
        MergeRequestView older = home.forge().opensADraft(forge, dispatched.launched().branch(), new BaseBranch("main"), "an-older-head");

        assertThatThrownBy(() -> home.deliverDraft().deliver(LAMP, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessageContaining("push the copy's head first");

        home.forge().closes(older.address());
        MergeRequestView atTheHead = home.forge().opensADraft(forge, dispatched.launched().branch(), new BaseBranch("main"), copy.head());
        home.forge().merges(atTheHead.address(), "3333");
        assertThatThrownBy(() -> home.deliverDraft().deliver(LAMP, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage(atTheHead.address() + " is merged already; edi merged fit-lamp follows its merge.");
        assertThat(home.deliveries().of(new TaskId("fit-lamp"))).isEmpty();
        assertThat(home.forge().openedDrafts()).isEmpty();
    }

    @Test
    void opensDraftsOnlyForShipsDeliveredForReview() throws IOException {
        DeliveryFixtures.dispatched(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3", DeliveryMode.LOCAL_BRANCH));

        assertThatThrownBy(() -> home.deliverDraft().deliver(LAMP, Actor.PRIMARY)).isInstanceOf(Refusal.class)
                .hasMessage("fit-lamp is delivered local-branch; edi deliver opens drafts for ships delivered draft-review only.");
    }

    private static FixtureRepository pushed(DispatchOutcome dispatched) throws IOException {
        FixtureRepository copy = DeliveryFixtures.committedIn(dispatched, "lamp.txt", "brass\n", "HRN-3 Fit the lamp");
        copy.run("config", "--worktree", "remote.origin.pushurl", DeliveryFixtures.PUSH_URL);
        return copy;
    }
}
