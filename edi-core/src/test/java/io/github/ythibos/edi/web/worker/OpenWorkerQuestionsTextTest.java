package io.github.ythibos.edi.web.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.application.worker.OpenWorkerQuestions;
import io.github.ythibos.edi.application.worker.OpenWorkerQuestions.OpenQuestion;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.WorkerId;

class OpenWorkerQuestionsTextTest {

    private final OpenWorkerQuestionsText text = new OpenWorkerQuestionsText();

    @Test
    void listsEachWorkerWaitingOnAnAnswerWithItsKeyAndAge() {
        OpenWorkerQuestions open = new OpenWorkerQuestions(List.of(
                new OpenQuestion(WorkerId.first(new TaskId("fit-lamp")), new StatusReport(StatusState.NEEDS_DECISION, new StatusKey("socket"), null,
                        "Which socket?"), Duration.ofMinutes(25)),
                new OpenQuestion(WorkerId.first(new TaskId("tune-horn")), new StatusReport(StatusState.BLOCKED, new StatusKey("reed"), null,
                        "The reed is cracked."), Duration.ofMinutes(1))));

        assertThat(text.text(open)).isEqualTo("""
                Workers waiting on an answer (2):
                  fit-lamp#1 (socket), 25 minutes: Which socket?
                  tune-horn#1 (reed), 1 minute: The reed is cracked.
                """);
    }

    @Test
    void saysSoWhenNoWorkerWaits() {
        assertThat(text.text(new OpenWorkerQuestions(List.of()))).isEqualTo("No worker waits on an answer.\n");
    }
}
