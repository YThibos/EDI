package io.github.ythibos.edi.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReachesTheCaptainTest {

    @Test
    void leavesWhatAWorkerReportsAsWorkAndWhatThePrimarySteersOutOfTheCaptainsList() {
        assertThat(ReachesTheCaptain.typeNames()).doesNotContain("StatusReported", "SteerSent", "SteerAcknowledged", "TaskAdded", "WorkerTurnEnded");
    }
}
