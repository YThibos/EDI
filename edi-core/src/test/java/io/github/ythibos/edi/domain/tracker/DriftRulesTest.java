package io.github.ythibos.edi.domain.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.project.WorkKey;

class DriftRulesTest {

    private static final Workflow WORKFLOW = new Workflow(List.of("To Do", "In Progress", "In Review", "In Test", "Ready For Prd", "Done"));
    private static final DriftRules RULES = new DriftRules(WORKFLOW, "In Test", List.of("In Review"), List.of("In Test", "Ready For Prd", "Done"), "Done",
            Set.of(WorkKey.parse("SVC-1")));

    @Test
    void reportsWorkMergedWhileTheTicketIsStillBeforeItsMergedState() {
        DriftReport report = reportOf(List.of(story("SVC-10", "In Progress"), story("SVC-11", "In Test"), story("SVC-12", "To Do")),
                sightings(Set.of(key("SVC-10"), key("SVC-11")), Set.of(), Set.of()));

        assertThat(report.drifts()).extracting(TicketDrift::key, TicketDrift::kind)
                .containsExactly(tuple(key("SVC-10"), DriftKind.MERGED_BEFORE_ITS_MERGED_STATE));
    }

    @Test
    void reportsAStoryOrBugInDoneBeforeItsWorkReachedProduction() {
        DriftReport report = reportOf(List.of(story("SVC-20", "Done"), bug("SVC-21", "Done"), story("SVC-22", "Done"), task("SVC-23", "Done")),
                sightings(Set.of(key("SVC-20"), key("SVC-21"), key("SVC-22"), key("SVC-23")), Set.of(key("SVC-22")), Set.of()));

        assertThat(report.drifts()).extracting(TicketDrift::key, TicketDrift::kind).containsExactly(
                tuple(key("SVC-20"), DriftKind.DONE_BEFORE_PRODUCTION),
                tuple(key("SVC-21"), DriftKind.DONE_BEFORE_PRODUCTION));
    }

    @Test
    void reportsATicketInReviewWithNoOpenRequest() {
        DriftReport report = reportOf(List.of(story("SVC-30", "In Review"), story("SVC-31", "In Review")),
                sightings(Set.of(), Set.of(), Set.of(key("SVC-31"))));

        assertThat(report.drifts()).extracting(TicketDrift::key, TicketDrift::kind)
                .containsExactly(tuple(key("SVC-30"), DriftKind.IN_REVIEW_WITHOUT_AN_OPEN_REQUEST));
    }

    @Test
    void reportsATicketInAnImplementedStateWithNoCommitOnTheBase() {
        DriftReport report = reportOf(List.of(story("SVC-40", "In Test"), story("SVC-41", "Ready For Prd"), story("SVC-42", "In Review")),
                sightings(Set.of(key("SVC-41")), Set.of(), Set.of()));

        assertThat(report.drifts()).filteredOn(drift -> drift.kind() == DriftKind.IMPLEMENTED_WITHOUT_A_COMMIT)
                .extracting(TicketDrift::key).containsExactly(key("SVC-40"));
    }

    @Test
    void leavesEvergreenTicketsAlone() {
        DriftReport report = reportOf(List.of(story("SVC-1", "Done")), sightings(Set.of(), Set.of(), Set.of()));

        assertThat(report.drifts()).isEmpty();
    }

    @Test
    void saysWhichChecksItCouldNotMakeInsteadOfPassingTheirTicketsSilently() {
        DriftRules unconfigured = new DriftRules(WORKFLOW, "In Test", List.of(), List.of(), null, Set.of());
        RepositorySightings withoutEvidence = new RepositorySightings(Set.of(), Sighting.unavailable("the project's registry entry names no production branch"),
                Sighting.unavailable("the project's registry entry names no forge"));

        DriftReport report = unconfigured.report("service", List.of(story("SVC-50", "Done"), story("SVC-51", "In Review")), withoutEvidence);

        assertThat(report.drifts()).isEmpty();
        assertThat(report.unchecked()).containsExactly(
                "in an implemented state with no commit on the base branch, because the machine profile names no tracker.implementedStatuses",
                "a story or bug in Done before its work reached production, because the machine profile names no tracker.doneStatus",
                "in a review state with no open request, because the machine profile names no tracker.reviewStatuses");
    }

    @Test
    void namesTheMissingEvidenceWhenTheProfileIsConfigured() {
        RepositorySightings withoutEvidence = new RepositorySightings(Set.of(), Sighting.unavailable("the project's registry entry names no production branch"),
                Sighting.unavailable("the project's registry entry names no forge"));

        DriftReport report = RULES.report("service", List.of(story("SVC-50", "Done"), story("SVC-51", "In Review")), withoutEvidence);

        assertThat(report.drifts()).extracting(TicketDrift::kind).doesNotContain(DriftKind.DONE_BEFORE_PRODUCTION, DriftKind.IN_REVIEW_WITHOUT_AN_OPEN_REQUEST);
        assertThat(report.unchecked()).containsExactly(
                "a story or bug in Done before its work reached production, because the project's registry entry names no production branch",
                "in a review state with no open request, because the project's registry entry names no forge");
    }

    @Test
    void ignoresATicketWhoseStatusIsOutsideTheWorkflowWhenJudgingItsMerge() {
        DriftReport report = reportOf(List.of(story("SVC-60", "Blocked"), story("SVC-61", "In Test")), sightings(Set.of(key("SVC-60")), Set.of(), Set.of()));

        assertThat(report.drifts()).extracting(TicketDrift::key, TicketDrift::kind)
                .containsExactly(tuple(key("SVC-61"), DriftKind.IMPLEMENTED_WITHOUT_A_COMMIT));
    }

    private static DriftReport reportOf(List<ProjectTicket> tickets, RepositorySightings sightings) {
        return RULES.report("service", tickets, sightings);
    }

    private static RepositorySightings sightings(Set<WorkKey> landed, Set<WorkKey> inProduction, Set<WorkKey> underRequest) {
        return new RepositorySightings(landed, Sighting.of(inProduction), Sighting.of(underRequest));
    }

    private static ProjectTicket story(String key, String status) {
        return new ProjectTicket(new Ticket(key(key), "Summary of " + key, status), "Story");
    }

    private static ProjectTicket bug(String key, String status) {
        return new ProjectTicket(new Ticket(key(key), "Summary of " + key, status), "Bug");
    }

    private static ProjectTicket task(String key, String status) {
        return new ProjectTicket(new Ticket(key(key), "Summary of " + key, status), "Task");
    }

    private static WorkKey key(String key) {
        return WorkKey.parse(key);
    }
}
