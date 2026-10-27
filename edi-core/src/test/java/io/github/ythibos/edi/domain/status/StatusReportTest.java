package io.github.ythibos.edi.domain.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.ythibos.edi.domain.Refusal;

class StatusReportTest {

    private static final Instant PAUSE_ENDS = Instant.parse("2026-10-16T14:30:00Z");
    private static final StatusKey LAMP = new StatusKey("lamp");

    @ParameterizedTest
    @EnumSource(value = StatusState.class, names = {"NEEDS_DECISION", "BLOCKED"})
    void refusesAQuestionWithoutTheKeyThatSettlesIt(StatusState question) {
        assertThatThrownBy(() -> fromWorker(question, null, null)).isInstanceOf(Refusal.class)
                .hasMessage("A " + question.word() + " status names what it waits on with --key, so the message that answers it can settle it.");
        assertThat(fromWorker(question, LAMP, null).key()).isEqualTo(LAMP);
    }

    @ParameterizedTest
    @EnumSource(value = StatusState.class, names = "PAUSED")
    void refusesAPauseWithoutItsEnd(StatusState pause) {
        assertThatThrownBy(() -> fromWorker(pause, null, null)).isInstanceOf(Refusal.class).hasMessageContaining("--until");
        assertThat(fromWorker(pause, null, PAUSE_ENDS).until()).isEqualTo(PAUSE_ENDS);
    }

    @ParameterizedTest
    @EnumSource(value = StatusState.class, names = {"WORKING", "DONE", "FAILED"})
    void asksNeitherKeyNorEndOfAnyOtherState(StatusState state) {
        assertThat(fromWorker(state, null, null).state()).isEqualTo(state);
    }

    private static StatusReport fromWorker(StatusState state, StatusKey key, Instant until) {
        return StatusReport.fromWorker(state, key, until, "The lamp, " + state.word() + ".", CaptainAddress.DEFAULT);
    }
}
