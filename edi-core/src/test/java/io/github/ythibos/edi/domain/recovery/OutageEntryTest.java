package io.github.ythibos.edi.domain.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.task.HoldKind;
import io.github.ythibos.edi.domain.task.HoldUntil;
import io.github.ythibos.edi.domain.task.TaskId;
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

        assertThat(entry.hold().kind()).isEqualTo(HoldKind.APPROVAL);
        assertThat(entry.hold().until()).isEqualTo(HoldUntil.captainsWord());
        assertThat(entry.hold().reason()).isEqualTo("lost when the machine restarted; number 2 on the outage hold list waits for the captain to resume it or start it fresh");
        assertThat(entry.holds(entry.hold())).isTrue();
        assertThat(entry(3, "tune-horn").holds(entry.hold())).isFalse();
    }

    @Test
    void readsTheCaptainsAnswerWords() {
        assertThat(RecoveryAction.named("resume")).isEqualTo(RecoveryAction.RESUME);
        assertThat(RecoveryAction.named("fresh")).isEqualTo(RecoveryAction.FRESH);
        assertThatIllegalArgumentException().isThrownBy(() -> RecoveryAction.named("drop")).withMessageContaining("resume or fresh");
    }

    private static OutageEntry entry(int number, String task) {
        return new OutageEntry(number, new TaskId(task), WorkerId.first(new TaskId(task)), DeathCause.MACHINE_RESTARTED, LISTED);
    }
}
