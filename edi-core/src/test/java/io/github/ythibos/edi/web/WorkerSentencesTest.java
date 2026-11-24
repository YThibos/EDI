package io.github.ythibos.edi.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerId;

class WorkerSentencesTest {

    @Test
    void namesTheWorkersADeferralCounts() {
        DispatchOutcome.Deferred deferred = new DispatchOutcome.Deferred(new TaskId("fit-bell"), new ProjectName("harness"),
                List.of(WorkerId.first(new TaskId("fit-lamp")), WorkerId.first(new TaskId("fit-horn"))), 2);

        assertThat(WorkerSentences.deferred(deferred)).isEqualTo("Deferred fit-bell: harness has 2 of its 2 workers at work (fit-lamp#1, fit-horn#1),"
                + " and the task starts as soon as one of them stops working.");
    }
}
