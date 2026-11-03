package io.github.ythibos.edi.web.wake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.adapter.store.WakeStore.QueuedWake;
import io.github.ythibos.edi.application.wake.WakeDelivery;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.event.DomainEvent.WorkerStopMissed;
import io.github.ythibos.edi.domain.presence.Presence;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.wake.Wake;
import io.github.ythibos.edi.domain.wake.WakeKind;
import io.github.ythibos.edi.domain.wake.WakeRule;
import io.github.ythibos.edi.domain.worker.WorkerId;
import io.github.ythibos.edi.web.PresenterFixtures;

class WakeDigestTextTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");
    private static final Instant RAISED = Instant.parse("2026-10-16T09:00:00Z");
    private static final Presence PRESENT = Presence.presentSince(RAISED);

    private final WakeDigestText digest = new WakeDigestText(PresenterFixtures.moments(), PresenterFixtures.presenceWords());

    @Test
    void tellsThePrimaryWhatNeedsItOneNumberedLineAtATime() {
        WakeDelivery delivery = new WakeDelivery(List.of(
                queued(1, new Wake(WakeKind.NEEDS_DECISION, LAMP, WorkerId.first(LAMP), "(lamp) Brass or oil?", null)),
                queued(2, new Wake(WakeKind.WORKER_DIED, LAMP, WorkerId.first(LAMP), "its tmux server changed", null)),
                queued(3, new Wake(WakeKind.USAGE_LIMIT, LAMP, WorkerId.first(LAMP), "session limit", Instant.parse("2026-10-16T14:10:00Z"))),
                queued(4, new Wake(WakeKind.LOST_IN_OUTAGE, new TaskId("tune-horn"), null, "2: the machine restarted", null))), 1, 3, 1, PRESENT, true);

        assertThat(digest.text(delivery)).isEqualTo("""
                EDI wake: 4 waiting.
                1. fit-lamp#1 needs a decision: (lamp) Brass or oil?
                2. fit-lamp#1 died: its tmux server changed; edi worker relaunch fit-lamp resumes its session.
                3. fit-lamp#1 stopped on the session limit; you type one resume line after 2026-10-16 14:10 UTC.
                4. tune-horn is on the outage hold list as number 2: the machine restarted; edi recover shows the list.
                Fleet: 1 open decision, 3 tasks in flight, 1 on the outage hold list.
                Handle these; the wake stream keeps running and prints the next.
                """);
    }

    @Test
    void countsTheFleetInTheSingularWhenOneTaskIsInFlight() {
        WakeDelivery delivery = new WakeDelivery(List.of(queued(1, new Wake(WakeKind.HOLD_DUE, LAMP, null, "2026-11-03", null))), 2, 1, 0, PRESENT, true);

        assertThat(digest.text(delivery)).contains("Fleet: 2 open decisions, 1 task in flight, 0 on the outage hold list.\n");
    }

    @Test
    void tellsThePrimaryThatAFullContextNeedsAFreshSession() {
        WakeDelivery delivery = new WakeDelivery(List.of(queued(1, new Wake(WakeKind.CONTEXT_FULL, LAMP, WorkerId.first(LAMP), "", RAISED))), 0, 1, 0,
                PRESENT, true);

        assertThat(digest.text(delivery)).contains("1. fit-lamp#1's context has been full since 2026-10-16 09:00 UTC, so it can take no further prompt and"
                + " a resume would stop on it again; edi worker stop fit-lamp, then edi worker relaunch fit-lamp starts a fresh session in its copy with a"
                + " handover.\n");
    }

    @Test
    void tellsThePrimaryToWarnTheCaptainOfHeavyRunsAtOnce() {
        WakeDelivery delivery = new WakeDelivery(List.of(queued(1, new Wake(WakeKind.HEAVY_RUNS_AT_ONCE, LAMP, WorkerId.first(LAMP),
                "a native build while tune-horn#1 runs a test run", null))), 0, 2, 0, PRESENT, true);

        assertThat(digest.text(delivery)).contains("1. fit-lamp#1 started a native build while tune-horn#1 runs a test run; nothing caps them, so each runs"
                + " slower until they finish: tell the captain, whose report warns while they overlap.\n");
    }

    @Test
    void tellsThePrimaryThatEdiWokeAWorkerWhoseStopItMissed() {
        Wake missed = WakeRule.wakeFor(new WorkerStopMissed(LAMP, WorkerId.first(LAMP), RAISED, 2, true), Actor.CORE).orElseThrow();
        Wake untyped = WakeRule.wakeFor(new WorkerStopMissed(LAMP, WorkerId.first(LAMP), RAISED, 2, false), Actor.CORE).orElseThrow();
        WakeDelivery delivery = new WakeDelivery(List.of(queued(1, missed), queued(2, untyped)), 0, 1, 0, PRESENT, true);

        assertThat(digest.text(delivery)).contains("1. fit-lamp#1 ended its turn at 2026-10-16 09:00 UTC while you could not take its stop hook, so you"
                + " sent it a message to carry on: message 2 was typed into its idle prompt.\n")
                .contains("2. fit-lamp#1 ended its turn at 2026-10-16 09:00 UTC while you could not take its stop hook, so you sent it a message to"
                        + " carry on: message 2 waits, as its pane did not take the notice; edi worker peek fit-lamp shows the pane.\n");
    }

    @Test
    void tellsThePrimaryWhatHappenedToADelivery() {
        WakeDelivery delivery = new WakeDelivery(List.of(
                queued(1, new Wake(WakeKind.DRAFT_MERGED, LAMP, null, "https://github.com/example-owner/harness/pull/7", null)),
                queued(2, new Wake(WakeKind.DRAFT_CLOSED, LAMP, null, "https://github.com/example-owner/harness/pull/7", null)),
                queued(3, new Wake(WakeKind.DELIVERY_FAILED, LAMP, null, "the captain's checkout has uncommitted work in lamp.txt", null)),
                queued(4, new Wake(WakeKind.CLEANUP_REFUSED, LAMP, null, "notes.txt is not committed", null))), 0, 1, 0, PRESENT, true);

        assertThat(digest.text(delivery)).contains("""
                1. fit-lamp: the captain merged https://github.com/example-owner/harness/pull/7; you refresh the clone and clean up.
                2. fit-lamp: https://github.com/example-owner/harness/pull/7 was closed without a merge; its work waits for the captain's word.
                3. fit-lamp: its delivery to the captain's checkout failed: the captain's checkout has uncommitted work in lamp.txt
                4. fit-lamp was not cleaned up after its merge: notes.txt is not committed
                """);
    }

    @Test
    void namesEveryKindOfWake() {
        for (WakeKind kind : WakeKind.values()) {
            String text = digest.text(new WakeDelivery(List.of(queued(1, new Wake(kind, LAMP, null, "detail", RAISED))), 0, 0, 0, PRESENT, true));
            assertThat(text).as(kind.word()).contains("1. fit-lamp");
        }
    }

    @Test
    void asksForSilenceWhileTheCaptainKeepsTheChatQuietAndForTheNextWaitAfterAWaitForOneWake() {
        Presence quiet = PRESENT.quiet(RAISED.plusSeconds(60));
        WakeDelivery delivery = new WakeDelivery(List.of(queued(1, new Wake(WakeKind.DONE, LAMP, WorkerId.first(LAMP), "fitted", null))), 0, 1, 0, quiet, false);

        assertThat(digest.text(delivery)).contains("The captain is quiet since 2026-10-16 09:01 UTC, on request: handle these and end"
                + " the turn with no text for the captain; decisions, failures, blockers and ready drafts reach the captain as notifications and in their"
                + " return brief.\n"
                + "Handle these, then start edi wait in the background again.\n");
    }

    private static QueuedWake queued(long number, Wake wake) {
        return new QueuedWake(number, wake, RAISED, UUID.randomUUID());
    }
}
