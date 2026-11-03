package io.github.ythibos.edi.application.worker;

import java.util.List;

import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.worker.Worker;

public record WorkerDetail(Worker worker, List<InboxMessage> steers, PipelineRun pipeline) {

    public WorkerDetail {
        steers = List.copyOf(steers);
    }
}
