package io.github.ythibos.edi.application.worker;

import java.time.Duration;
import java.util.List;

import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.worker.WorkerId;

public record OpenWorkerQuestions(List<OpenQuestion> questions) {

    public OpenWorkerQuestions {
        questions = List.copyOf(questions);
    }

    public record OpenQuestion(WorkerId worker, StatusReport question, Duration waited) {
    }
}
