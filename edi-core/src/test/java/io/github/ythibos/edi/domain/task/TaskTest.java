package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.project.ProjectName;

class TaskTest {

    private static final Instant AT = Instant.parse("2026-10-15T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-16T09:00:00Z");
    private static final CaptainHold DEFERRAL = new CaptainHold(HoldKind.DEFERRAL, "after the season", HoldUntil.day(LocalDate.of(2026, 11, 1)), AT);
    private static final CaptainsWord DROP = new CaptainsWord("Drop it, we fit no lamps this year.");
    private static final CaptainHold DECISION = new CaptainHold(HoldKind.DECISION, "D58 Which lamp?", HoldUntil.captainsWord(), AT);

    @Test
    void entersTheQueueWithItsNotes() {
        Task task = ship().noted("First paragraph.").noted("  Second paragraph.  ");

        assertThat(task.state()).isEqualTo(TaskState.QUEUED);
        assertThat(task.notes()).isEqualTo("Intake notes.\n\nFirst paragraph.\n\nSecond paragraph.");
        assertThat(task.created()).isEqualTo(AT);
    }

    @Test
    void refusesAnEmptyNote() {
        assertThatIllegalArgumentException().isThrownBy(() -> ship().noted("  \n "));
    }

    @Test
    void waitsForTheCaptainUntilReleased() {
        Task held = ship().held(DEFERRAL);

        assertThat(held.isHeld()).isTrue();
        assertThat(held.released().isHeld()).isFalse();
    }

    @Test
    void refusesASecondHold() {
        Task held = ship().held(DEFERRAL);

        assertThatThrownBy(() -> held.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("already held: after the season");
    }

    @Test
    void takesADecisionHoldOnlyFromItsDecision() {
        assertThatThrownBy(() -> ship().held(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("raised with its card");
    }

    @Test
    void letsADecisionReplaceADeferral() {
        assertThat(ship().held(DEFERRAL).heldForDecision(DECISION).hold()).isEqualTo(DECISION);
    }

    @Test
    void releasesADecisionHoldOnlyThroughTheAnswer() {
        Task waiting = ship().heldForDecision(DECISION);

        assertThatThrownBy(waiting::released).isInstanceOf(Refusal.class).hasMessageContaining("answering the decision releases it");
        assertThat(waiting.decisionAnswered(LATER).isHeld()).isFalse();
        assertThat(waiting.decisionAnswered(LATER).state()).isEqualTo(TaskState.QUEUED);
    }

    @Test
    void closesACaptainItemWithItsAnswer() {
        Task item = Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of()), "", AT)
                .heldForDecision(DECISION);

        Task decided = item.decisionAnswered(LATER);

        assertThat(decided.state()).isEqualTo(TaskState.DONE);
        assertThat(decided.closed()).isEqualTo(LATER);
        assertThat(decided.isHeld()).isFalse();
    }

    @Test
    void refusesToHoldOrReleaseWhatCannotWait() {
        Task done = Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of()), "", AT)
                .heldForDecision(DECISION)
                .decisionAnswered(LATER);

        assertThatThrownBy(() -> done.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("is done");
        assertThatThrownBy(() -> done.heldForDecision(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("is done");
        assertThatThrownBy(ship()::released).isInstanceOf(Refusal.class).hasMessageContaining("is not held");
        assertThatThrownBy(() -> ship().decisionAnswered(LATER)).isInstanceOf(Refusal.class).hasMessageContaining("not held for a decision");
    }

    @Test
    void dropsFromAnyStateOnTheCaptainsWord() {
        Task done = Task.queued(new TaskId("lamp-choice"), new Intake(TaskKind.CAPTAIN, "Choose the lamp", List.of(), null, null, List.of()), "", AT)
                .heldForDecision(DECISION)
                .decisionAnswered(LATER);

        for (Task task : List.of(ship(), ship().held(DEFERRAL), ship().heldForDecision(DECISION), done)) {
            Task dropped = task.dropped(DROP, LATER);

            assertThat(dropped.state()).isEqualTo(TaskState.DROPPED);
            assertThat(dropped.closed()).isEqualTo(LATER);
            assertThat(dropped.isHeld()).isFalse();
        }
    }

    @Test
    void dropsOnlyOnTheCaptainsWord() {
        assertThatThrownBy(() -> ship().dropped(null, LATER)).isInstanceOf(NullPointerException.class).hasMessageContaining("captain's word");
        assertThatIllegalArgumentException().isThrownBy(() -> new CaptainsWord("  "));
    }

    @Test
    void waitsForNobodyOnceDropped() {
        Task dropped = ship().dropped(DROP, LATER);

        assertThatThrownBy(() -> dropped.dropped(DROP, LATER)).isInstanceOf(Refusal.class).hasMessageContaining("already dropped");
        assertThatThrownBy(() -> dropped.held(DEFERRAL)).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
        assertThatThrownBy(() -> dropped.heldForDecision(DECISION)).isInstanceOf(Refusal.class).hasMessageContaining("is dropped");
    }

    @Test
    void keepsItsStateConsistent() {
        Intake intake = ship().intake();
        TaskId id = new TaskId("fit-lamp");

        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskState.DONE, "", null, AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskState.QUEUED, "", null, AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskState.DONE, "", DEFERRAL, AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskState.DROPPED, "", null, AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id, intake, TaskState.DROPPED, "", DEFERRAL, AT, LATER));
        assertThatIllegalArgumentException().isThrownBy(() -> new Task(id,
                new Intake(TaskKind.SHIP, "Fit", List.of(), null, null, List.of(id)), TaskState.QUEUED, "", null, AT, null));
    }

    private static Task ship() {
        return Task.queued(new TaskId("fit-lamp"),
                new Intake(TaskKind.SHIP, "Fit the lamp", List.of(new ProjectName("harness")), null, null, List.of()), "  Intake notes. ", AT);
    }
}
