package io.github.ythibos.edi.domain.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.Hold;
import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.worker.DeathCause;
import io.github.ythibos.edi.domain.worker.WorkerId;

class OutageEntryTest {

    private static final Instant LISTED = Instant.parse("2026-10-16T09:00:00Z");

    @Test
    void numbersTheListFromOneAndAfterTheHighestOpenNumber() {
        assertThat(OutageEntry.nextNumberAfter(List.of())).isEqualTo(1);
        assertThat(OutageEntry.nextNumberAfter(List.of(entry(1, "fit-lamp"), entry(3, "ring-bell")))).isEqualTo(4);
        assertThatIllegalArgumentException().isThrownBy(() -> entry(0, "fit-lamp")).withMessageContaining("numbered from 1");
    }

    @Test
    void holdsItsTaskForTheCaptainsWordAndKnowsItsOwnHold() {
        OutageEntry entry = entry(2, "tune-horn");
        Task lost = horn().held(entry.hold());

        assertThat(entry.hold().kind()).isEqualTo(HoldKind.OUTAGE);
        assertThat(entry.hold().until()).isEqualTo(HoldUntil.captainsWord());
        assertThat(entry.hold().reason()).isEqualTo("lost when the machine restarted; number 2 on the outage hold list waits for the captain to resume it or start it fresh");
        assertThat(entry.holds(lost)).isTrue();
        assertThat(entry.holds(horn())).isFalse();
    }

    @Test
    void releasesOnlyItsOwnHoldAndNamesTheOthers() {
        OutageEntry entry = entry(2, "tune-horn");
        Hold.Deferral deferral = new Hold.Deferral("after the season", HoldUntil.captainsWord(), LISTED);
        Task lost = horn().held(deferral).held(entry.hold());

        assertThat(entry.otherHoldsOf(lost)).containsExactly(deferral);
        assertThat(entry.withoutItsHold(lost).holds().held()).containsExactly(deferral);
        assertThat(entry.withoutItsHold(horn())).isEqualTo(horn());
    }

    @Test
    void readsTheCaptainsAnswerWords() {
        assertThat(RecoveryAction.named("resume")).isEqualTo(RecoveryAction.RESUME);
        assertThat(RecoveryAction.named("fresh")).isEqualTo(RecoveryAction.FRESH);
        assertThatIllegalArgumentException().isThrownBy(() -> RecoveryAction.named("drop")).withMessageContaining("resume or fresh");
    }

    private static Task horn() {
        return Task.queued(new TaskId("tune-horn"),
                new Intake(TaskKind.SHIP, "Tune the horn", List.of(new ProjectName("harness")), null, null, List.of(), null, null), "", LISTED);
    }

    private static OutageEntry entry(int number, String task) {
        return new OutageEntry(number, new TaskId(task), WorkerId.first(new TaskId(task)), DeathCause.MACHINE_RESTARTED, LISTED);
    }
}
