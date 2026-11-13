package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class WaitTest {

    private static final TaskId LANTERN = new TaskId("lantern");

    @Test
    void anAfterMergedWaitIsMetFromTheMergeOn() {
        assertThat(stagesMeeting(AwaitedStage.MERGED)).containsExactly(TaskStage.MERGED, TaskStage.LANDED, TaskStage.RETIRED, TaskStage.DROPPED);
    }

    @Test
    void anAfterLandedWaitIsMetOnlyOnceTheWorkLanded() {
        assertThat(stagesMeeting(AwaitedStage.LANDED)).containsExactly(TaskStage.LANDED, TaskStage.RETIRED, TaskStage.DROPPED);
    }

    @Test
    void aBlockerIsWaitedForUntilItLands() {
        assertThat(Wait.afterLanded(LANTERN, TaskStage.MERGED).isMet()).isFalse();
        assertThat(Wait.afterLanded(LANTERN, TaskStage.LANDED).isMet()).isTrue();
        assertThat(Wait.afterLanded(LANTERN, TaskStage.QUEUED).words()).isEqualTo("lantern to land");
        assertThat(new Wait(LANTERN, AwaitedStage.MERGED, TaskStage.QUEUED).words()).isEqualTo("lantern to be merged");
    }

    private static TaskStage[] stagesMeeting(AwaitedStage awaited) {
        return Arrays.stream(TaskStage.values()).filter(stage -> new Wait(LANTERN, awaited, stage).isMet()).toArray(TaskStage[]::new);
    }
}
