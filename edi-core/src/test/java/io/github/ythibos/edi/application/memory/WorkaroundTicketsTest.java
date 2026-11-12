package io.github.ythibos.edi.application.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.tracker.TrackerUnavailable;
import io.github.ythibos.edi.domain.memory.StowFinding;
import io.github.ythibos.edi.domain.memory.StowList;
import io.github.ythibos.edi.domain.memory.WorkaroundCount;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskStage;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;
import io.github.ythibos.edi.testing.WorkerFixtures;

class WorkaroundTicketsTest {

    private static final String LEARNINGS = """
            # Learnings

            - Queue the next step before the merge until EDI-69.
            - Book the worklog by hand until EDI-99.
            - Links open with Shift and a click (2026-10-15).
            - Peek every busy worker after an outage until HRN-7.
            - Light the harbour lamps by hand until SVC-40.
            - Read the request state from the forge until SVC-41.
            - Ring the harbour bell by hand until SVC-77.
            """;

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void rememberTheWorkarounds() throws IOException {
        home = TrackerFixtures.laptopHome(directory).writing("memory/learnings.md", LEARNINGS);
        home.jira().holding("SVC-41", "Request state on the page", "Testing");
        home.storing(List.of(WorkerFixtures.queuedShip("queue-next", "harness", "EDI-69"), WorkerFixtures.queuedShip("peek-workers", "harness", "HRN-7"),
                WorkerFixtures.queuedShip("light-lamps", "service", "SVC-40")));
        home.markDone(new TaskId("queue-next"));
    }

    @Test
    void stowListResolvesANativeKeyThroughTheStoreAndAJiraKeyThroughTheTracker() {
        StowList list = home.memory().stowList();

        assertThat(list.findings()).extracting(StowFinding::file, StowFinding::why).containsExactly(
                tuple("learnings.md", "expired: EDI-69 has landed, so delete it"),
                tuple("learnings.md", "unticketed: no task or ticket carries EDI-99; give it a ticket, or turn it into a fact dated today"),
                tuple("learnings.md", "expired: SVC-41 has landed, so delete it"),
                tuple("learnings.md", "unticketed: no task or ticket carries SVC-77; give it a ticket, or turn it into a fact dated today"));
        assertThat(list.workarounds()).isEqualTo(new WorkaroundCount(2, 2));
    }

    @Test
    void stowListListsAJiraKeyAsUncheckedWhenTheTrackerCannotAnswer() throws IOException {
        home.writing("memory/learnings.md", "- Read the request state from the forge until SVC-41.\n");
        home.jira().failsNextWith(new TrackerUnavailable("JIRA cannot be reached."));

        assertThat(home.memory().stowList().findings()).extracting(StowFinding::why)
                .containsExactly("unchecked: the tracker could not say whether SVC-41 has landed; list it again once the tracker answers");
    }

    @Test
    void theDigestCountsFromTheStoreAloneSoAJiraKeyWithoutALandedTaskIsNeither() {
        assertThat(home.memory().workarounds()).isEqualTo(new WorkaroundCount(1, 1));
    }

    @Test
    void theDigestCountsAJiraKeyAsExpiredWhenATaskCarryingItHasLanded() {
        home.markDone(new TaskId("light-lamps"));

        assertThat(home.memory().workarounds()).isEqualTo(new WorkaroundCount(2, 1));
    }

    @Test
    void countsAWorkaroundAsExpiredOnceItsTaskLandsAndNotWhileItIsOnlyMerged() {
        home.putAt(new TaskId("peek-workers"), TaskStage.MERGED);
        WorkaroundCount merged = home.memory().workarounds();

        home.putAt(new TaskId("peek-workers"), TaskStage.LANDED);

        assertThat(merged).isEqualTo(new WorkaroundCount(1, 1));
        assertThat(home.memory().workarounds()).isEqualTo(new WorkaroundCount(2, 1));
    }
}
