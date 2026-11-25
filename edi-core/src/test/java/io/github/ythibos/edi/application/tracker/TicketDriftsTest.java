package io.github.ythibos.edi.application.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.tracker.DriftKind;
import io.github.ythibos.edi.domain.tracker.DriftReport;
import io.github.ythibos.edi.domain.tracker.TicketDrift;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;

@Tag("adapter")
class TicketDriftsTest {

    private static final ProjectName SERVICE = new ProjectName("service");
    private static final String WITH_FORGE_AND_PRODUCTION = """
            projects:
              - name: service
                tracker: { kind: jira, projectKey: SVC }
                forge: { kind: github, path: example-owner/service, account: example-owner }
                base: main
                production: release
            """;

    @TempDir
    private Path directory;

    private TestHome home;
    private FixtureRepository upstream;

    @BeforeEach
    void keepAServiceWhoseWorkSitsOnMainAndPartlyOnItsReleaseBranch() throws IOException {
        home = TrackerFixtures.laptopHome(directory.resolve("home"));
        upstream = FixtureRepository.initialise(Files.createDirectories(directory.resolve("upstream")))
                .write("lamp.txt", "brass").commit("SVC-10 Fit the lamp")
                .write("buoy.txt", "red").commit("SVC-21 Paint the buoy")
                .branch("release").switchTo("main")
                .write("bell.txt", "bronze").commit("SVC-20 Ring the bell")
                .write("flag.txt", "blue").commit("SVC-100 Raise the flag")
                .write("typo.txt", "fixed").commit("Fix a typo");
        upstream.clonedInto(home.home().root().resolve("projects/service"));
        home.jira()
                .holding("SVC-10", "Fit the lamp", "In Progress")
                .holdingOfType("SVC-20", "Ring the bell", "Done", "Story")
                .holdingOfType("SVC-21", "Paint the buoy", "Done", "Bug")
                .holding("SVC-30", "Mend the net", "In Review")
                .holding("SVC-31", "Wax the hull", "In Review")
                .holding("SVC-41", "Trim the sail", "Testing");
    }

    @Test
    void listsEachKindOfDriftBetweenTheTicketsAndTheRepository() throws IOException {
        home.registering(WITH_FORGE_AND_PRODUCTION);
        RegisteredForge forge = home.registry().entryOf(SERVICE).forge();
        home.forge().opensADraft(forge, BranchName.forWork(BranchKind.FEATURE, WorkKey.parse("SVC-30")), new BaseBranch("main"), upstream.head());

        DriftReport report = home.ticketDrifts().of(SERVICE);

        assertThat(report.project()).isEqualTo("service");
        assertThat(report.drifts()).extracting(TicketDrift::key, TicketDrift::status, TicketDrift::kind).containsExactlyInAnyOrder(
                tuple(WorkKey.parse("SVC-10"), "In Progress", DriftKind.MERGED_BEFORE_ITS_MERGED_STATE),
                tuple(WorkKey.parse("SVC-20"), "Done", DriftKind.DONE_BEFORE_PRODUCTION),
                tuple(WorkKey.parse("SVC-31"), "In Review", DriftKind.IN_REVIEW_WITHOUT_AN_OPEN_REQUEST),
                tuple(WorkKey.parse("SVC-41"), "Testing", DriftKind.IMPLEMENTED_WITHOUT_A_COMMIT));
        assertThat(report.unchecked()).isEmpty();
    }

    @Test
    void doesNotCountAFollowUpBranchAsMissingItsRequest() throws IOException {
        home.registering(WITH_FORGE_AND_PRODUCTION);
        RegisteredForge forge = home.registry().entryOf(SERVICE).forge();
        BranchName followUp = BranchName.parse("feature/SVC-31-1");
        home.forge().opensADraft(forge, followUp, new BaseBranch("main"), upstream.head());
        home.forge().opensADraft(forge, BranchName.forWork(BranchKind.RESEARCH, WorkKey.parse("SVC-30")), new BaseBranch("main"), upstream.head());

        DriftReport report = home.ticketDrifts().of(SERVICE);

        assertThat(report.drifts()).extracting(TicketDrift::kind).doesNotContain(DriftKind.IN_REVIEW_WITHOUT_AN_OPEN_REQUEST);
    }

    @Test
    void saysWhatItCouldNotCheckWhenTheProjectNamesNoForgeOrProductionBranch() throws IOException {
        home.registering(TrackerFixtures.REGISTRY);

        DriftReport report = home.ticketDrifts().of(SERVICE);

        assertThat(report.unchecked()).containsExactly(
                "a story or bug in Done before its work reached production: not checked, because the project's registry entry names no production branch",
                "in a review state with no open request: not checked, because the project's registry entry names no forge");
        assertThat(report.drifts()).extracting(TicketDrift::kind)
                .containsExactlyInAnyOrder(DriftKind.MERGED_BEFORE_ITS_MERGED_STATE, DriftKind.IMPLEMENTED_WITHOUT_A_COMMIT);
    }

    @Test
    void refusesAProjectThatDoesNotBookItsWorkOnJira() throws IOException {
        home.registering(TrackerFixtures.REGISTRY);

        assertThatThrownBy(() -> home.ticketDrifts().of(new ProjectName("harness"))).isInstanceOf(Refusal.class)
                .hasMessage("harness books its work on edi, not on JIRA.");
    }
}
