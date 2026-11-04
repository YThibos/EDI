package io.github.ythibos.edi.web.worker;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.worker.WorkerDetail;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.status.StatusReport;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
public class WorkerDetailText implements TextPresenter<WorkerDetail> {

    private static final String INDENT = "  ";
    private static final String BODY_INDENT = "     ";

    private final Moments moments;

    public WorkerDetailText(Moments moments) {
        this.moments = moments;
    }

    @Override
    public Class<WorkerDetail> presents() {
        return WorkerDetail.class;
    }

    @Override
    public String text(WorkerDetail detail) {
        Worker worker = detail.worker();
        List<String> lines = new ArrayList<>(List.of(worker.id() + ", " + worker.category().value() + ", on " + worker.route()));
        lines.add("State: " + stateOf(worker) + ".");
        lines.addAll(doneCheckOf(worker));
        lines.addAll(lastStatusOf(worker));
        lines.addAll(openQuestionOf(worker));
        lines.addAll(steersOf(detail.steers()));
        lines.add(pipelineOf(detail.pipeline()));
        return String.join("\n", lines) + "\n";
    }

    private String stateOf(Worker worker) {
        if (worker.isRetired()) {
            return "retired at " + moments.minute(worker.retiredAt());
        }
        if (worker.stopRequestedAt() != null) {
            return "stop requested at " + moments.minute(worker.stopRequestedAt());
        }
        if (worker.launchFailedAt() != null) {
            return "launch failed at " + moments.minute(worker.launchFailedAt());
        }
        if (worker.endedAt() != null) {
            return "its session ended at " + moments.minute(worker.endedAt());
        }
        if (worker.limitPark() != null) {
            return "parked on a usage limit since " + moments.minute(worker.limitPark().parkedAt());
        }
        if (worker.contextFullSince() != null) {
            return "its context is full since " + moments.minute(worker.contextFullSince());
        }
        return worker.activity().word() + " since " + moments.minute(worker.activitySince());
    }

    private static List<String> doneCheckOf(Worker worker) {
        return worker.doneCheck() == null ? List.of() : List.of("Done check: " + worker.doneCheck().word() + ".");
    }

    private List<String> lastStatusOf(Worker worker) {
        if (worker.lastStatus() == null) {
            return List.of("Last status: none yet.");
        }
        StatusReport report = worker.lastStatus().report();
        List<String> lines = new ArrayList<>(List.of("Last status, " + moments.minute(worker.lastStatus().at()) + ": " + report.state().word()
                + (report.key() == null ? "" : " (" + report.key() + ")")));
        report.text().lines().forEach(line -> lines.add(INDENT + line));
        return lines;
    }

    private List<String> openQuestionOf(Worker worker) {
        if (!worker.waitsOnThePrimary()) {
            return List.of();
        }
        StatusReport report = worker.lastStatus().report();
        String question = report.key() == null ? "Open question" : "Open question " + report.key();
        String answer = report.key() == null ? "" : "; edi steer " + worker.id().task() + " --resolves " + report.key() + " answers it";
        return List.of(question + ": it waits on the primary since " + moments.minute(worker.lastStatus().at()) + answer + ".");
    }

    private List<String> steersOf(List<InboxMessage> steers) {
        List<String> lines = new ArrayList<>(List.of(Words.counted("Steers", steers.size())));
        steers.forEach(steer -> {
            lines.add(INDENT + steer.number() + "  " + deliveryOf(steer) + (steer.resolves() == null ? "" : ", resolving " + steer.resolves()));
            steer.body().lines().forEach(line -> lines.add(BODY_INDENT + line));
        });
        return lines;
    }

    private String deliveryOf(InboxMessage steer) {
        String sent = "sent " + moments.minute(steer.createdAt());
        if (steer.acknowledgedAt() != null) {
            return sent + ", acknowledged " + moments.minute(steer.acknowledgedAt());
        }
        return steer.receivedAt() == null ? sent + ", not yet received" : sent + ", received " + moments.minute(steer.receivedAt()) + ", not acknowledged";
    }

    private static String pipelineOf(PipelineRun run) {
        if (run == null) {
            return "Pipeline: no run yet.";
        }
        String step = run.activeStep() == null || run.activeStep().isBlank() ? "" : " at its " + run.activeStep() + " step";
        return "Pipeline: run " + run.id() + step + ", " + run.status() + ".";
    }
}
