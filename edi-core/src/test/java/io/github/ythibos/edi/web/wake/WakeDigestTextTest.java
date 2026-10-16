package io.github.ythibos.edi.web.wake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.application.wake.WakeDelivery;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.worker.WorkerId;

class WakeDigestTextTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final Instant RAISED = Instant.parse("2026-10-16T09:00:00Z");

    @Test
    void tellsThePrimaryWhatNeedsItOneNumberedLineAtATime() {
        WakeDelivery delivery = new WakeDelivery(List.of(
                queued(1, new Wake(WakeKind.NEEDS_DECISION, LAMP, WorkerId.first(LAMP), "(lamp) Brass or oil?", null)),
                queued(2, new Wake(WakeKind.WORKER_DIED, LAMP, WorkerId.first(LAMP), "its tmux server changed", null)),
                queued(3, new Wake(WakeKind.USAGE_LIMIT, LAMP, WorkerId.first(LAMP), "session limit", Instant.parse("2026-10-16T14:10:00Z"))),
                queued(4, new Wake(WakeKind.LOST_IN_OUTAGE, new TaskId("tune-horn"), null, "2: the machine restarted", null))), 1, 3, 1);

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
    void namesEveryKindOfWake() {
        for (WakeKind kind : WakeKind.values()) {
            String text = new WakeDigestText().text(new WakeDelivery(List.of(queued(1, new Wake(kind, LAMP, null, "detail", RAISED))), 0, 0, 0));
            assertThat(text).as(kind.word()).contains("1. fit-lamp");
        }
    }

    private static QueuedWake queued(long number, Wake wake) {
        return new QueuedWake(number, wake, RAISED, UUID.randomUUID());
    }
}
