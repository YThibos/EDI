package io.github.ythibos.edi.web.wake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.application.wake.WakeDelivery;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.WorkerId;

class WakeDigestTextTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final Instant RAISED = Instant.parse("2026-10-16T09:00:00Z");
    private static final Presence PRESENT = Presence.presentSince(RAISED);

    @Test
    void tellsThePrimaryWhatNeedsItOneNumberedLineAtATime() {
        WakeDelivery delivery = new WakeDelivery(List.of(
                queued(1, new Wake(WakeKind.NEEDS_DECISION, LAMP, WorkerId.first(LAMP), "(lamp) Brass or oil?", null)),
                queued(2, new Wake(WakeKind.WORKER_DIED, LAMP, WorkerId.first(LAMP), "its tmux server changed", null)),
                queued(3, new Wake(WakeKind.USAGE_LIMIT, LAMP, WorkerId.first(LAMP), "session limit", Instant.parse("2026-10-16T14:10:00Z"))),
                queued(4, new Wake(WakeKind.LOST_IN_OUTAGE, new TaskId("tune-horn"), null, "2: the machine restarted", null))), 1, 3, 1, PRESENT);

        assertThat(new WakeDigestText().text(delivery)).isEqualTo("""
                EDI wake: 4 waiting.
                1. fit-lamp#1 needs a decision: (lamp) Brass or oil?
                2. fit-lamp#1 died: its tmux server changed; edi worker relaunch fit-lamp resumes its session.
                3. fit-lamp#1 stopped on the session limit; EDI types one resume line after 2026-10-16 14:10Z.
                4. tune-horn is on the outage hold list as number 2: the machine restarted; edi recover shows the list.
                Fleet: 1 open decisions, 3 tasks in flight, 1 on the outage hold list.
                Handle these, then start edi wait in the background again.
                """);
    }

    @Test
    void tellsThePrimaryWhatHappenedToADelivery() {
        WakeDelivery delivery = new WakeDelivery(List.of(
                queued(1, new Wake(WakeKind.DRAFT_MERGED, LAMP, null, "https://github.com/example-owner/harness/pull/7", null)),
                queued(2, new Wake(WakeKind.DRAFT_CLOSED, LAMP, null, "https://github.com/example-owner/harness/pull/7", null)),
                queued(3, new Wake(WakeKind.DELIVERY_FAILED, LAMP, null, "the captain's checkout has uncommitted work in lamp.txt", null)),
                queued(4, new Wake(WakeKind.CLEANUP_REFUSED, LAMP, null, "notes.txt is not committed", null))), 0, 1, 0, PRESENT);

        assertThat(new WakeDigestText().text(delivery)).contains("""
                1. fit-lamp: the captain merged https://github.com/example-owner/harness/pull/7; EDI refreshes the clone and cleans up.
                2. fit-lamp: https://github.com/example-owner/harness/pull/7 was closed without a merge; its work waits for the captain's word.
                3. fit-lamp: its delivery to the captain's checkout failed: the captain's checkout has uncommitted work in lamp.txt
                4. fit-lamp was not cleaned up after its merge: notes.txt is not committed
                """);
    }

    @Test
    void namesEveryKindOfWake() {
        for (WakeKind kind : WakeKind.values()) {
            String text = new WakeDigestText().text(new WakeDelivery(List.of(queued(1, new Wake(kind, LAMP, null, "detail", RAISED))), 0, 0, 0, PRESENT));
            assertThat(text).as(kind.word()).contains("1. fit-lamp");
        }
    }

    @Test
    void asksForSilenceWhileTheCaptainKeepsTheChatQuiet() {
        Presence quiet = PRESENT.quiet(RAISED.plusSeconds(60));
        WakeDelivery delivery = new WakeDelivery(List.of(queued(1, new Wake(WakeKind.DONE, LAMP, WorkerId.first(LAMP), "fitted", null))), 0, 1, 0, quiet);

        assertThat(new WakeDigestText().text(delivery)).contains("The captain is quiet since 2026-10-16 09:01Z, on request: handle these and end"
                + " the turn with no text for the captain; decisions, failures, blockers and ready drafts reach the captain as notifications and in their"
                + " return brief.\n"
                + "Handle these, then start edi wait in the background again.\n");
    }

    private static QueuedWake queued(long number, Wake wake) {
        return new QueuedWake(number, wake, RAISED, UUID.randomUUID());
    }
}
