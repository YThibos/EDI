package io.github.ythibos.edi.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.project.WorkKey;

class StowListTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final WorkKey LANDED = WorkKey.parse("EDI-69");
    private static final WorkKey NOBODY_CARRIES = WorkKey.parse("EDI-99");
    private static final WorkKey ON_JIRA = WorkKey.parse("SVC-41");
    private static final WorkKey STILL_OPEN = WorkKey.parse("EDI-70");
    private static final WorkKey UNANSWERED = WorkKey.parse("SVC-42");
    private static final MemoryFile LEARNINGS = new MemoryFile(MemoryFile.LEARNINGS, """
            # Learnings

            - Queue the next step before the merge until EDI-69.
            - Book the worklog by hand until EDI-99
            - Read the request state from the forge before advising a merge order,
              because the snapshot lags, until SVC-41.
            - Links open with Shift and a click (2026-10-28).
            - Peek every busy worker after an outage until EDI-70.
            - Ask the tracker by hand until SVC-42.
            """);
    private static final Map<WorkKey, TicketStanding> TICKETS = Map.of(LANDED, TicketStanding.LANDED, NOBODY_CARRIES, TicketStanding.UNTICKETED,
            ON_JIRA, TicketStanding.LANDED, STILL_OPEN, TicketStanding.OPEN);

    @Test
    void readsTheKeyAWorkaroundEndsWith() {
        assertThat(LEARNINGS.entries()).extracting(MemoryEntry::until).containsExactly(Optional.of(LANDED), Optional.of(NOBODY_CARRIES),
                Optional.of(ON_JIRA), Optional.empty(), Optional.of(STILL_OPEN), Optional.of(UNANSWERED));
        assertThat(new MemoryFile(MemoryFile.LEARNINGS, "- Wait until EDI-12 lands, then rebuild (2026-10-28).\n").workarounds()).isEmpty();
        assertThat(new MemoryFile("profile.md", "- Keep the old flag until EDI-12.\n").workarounds()).isEmpty();
    }

    @Test
    void listsAnExpiredWorkaroundToDeleteAndAnUnticketedOneToTicketButNeitherADatedFactNorAnOpenTicket() {
        StowList list = StowList.of(new StartupMemory(List.of(LEARNINGS), 18_000), TODAY, TICKETS);

        assertThat(list.findings()).extracting(StowFinding::entry, StowFinding::why).containsExactly(
                tuple("Queue the next step before the merge until EDI-69.", "expired: EDI-69 has landed, so delete it"),
                tuple("Book the worklog by hand until EDI-99",
                        "unticketed: no task or ticket carries EDI-99; give it a ticket, or turn it into a fact dated today"),
                tuple("Read the request state from the forge before advising a merge order,", "expired: SVC-41 has landed, so delete it"),
                tuple("Ask the tracker by hand until SVC-42.",
                        "unchecked: the tracker could not say whether SVC-42 has landed; list it again once the tracker answers"));
        assertThat(list.workarounds()).isEqualTo(new WorkaroundCount(2, 1));
    }

    @Test
    void countsNothingToCurateWhenEveryWorkaroundWaitsOnAnOpenTicket() {
        MemoryFile learnings = new MemoryFile(MemoryFile.LEARNINGS, "- Peek every busy worker after an outage until EDI-70.\n");

        WorkaroundCount count = WorkaroundCount.of(new StartupMemory(List.of(learnings), 18_000), TICKETS);

        assertThat(count).isEqualTo(new WorkaroundCount(0, 0));
        assertThat(count.anyToCurate()).isFalse();
    }
}
