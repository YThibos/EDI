package io.github.ythibos.edi.web.task;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.task.TaskDetail;
import io.github.ythibos.edi.domain.delivery.Delivery;
import io.github.ythibos.edi.domain.event.RecordedEvent;
import io.github.ythibos.edi.domain.task.Intake;
import io.github.ythibos.edi.domain.task.StageReached;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.WorkedTime;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.web.decision.DecisionLines;
import io.github.ythibos.edi.web.text.Moments;
import io.github.ythibos.edi.web.text.Table;
import io.github.ythibos.edi.web.text.TextPresenter;
import io.github.ythibos.edi.web.text.Words;

@Component
class TaskDetailText implements TextPresenter<TaskDetail> {

    private static final String INDENT = "  ";
    private static final int MINUTES_PER_HOUR = 60;

    private final Moments moments;
    private final DecisionLines decisionLines;

    TaskDetailText(Moments moments, DecisionLines decisionLines) {
        this.moments = moments;
        this.decisionLines = decisionLines;
    }

    @Override
    public Class<TaskDetail> presents() {
        return TaskDetail.class;
    }

    @Override
    public String text(TaskDetail detail) {
        List<String> lines = new ArrayList<>(summaryOf(detail.task()));
        if (detail.worked() != null) {
            lines.add(workedLineOf(detail.worked()));
        }
        if (!detail.waits().isEmpty()) {
            lines.add(capitalised(Words.waits(detail.waits())));
        }
        if (detail.task().isHeld()) {
            lines.add(capitalised(BacklogText.holdOf(detail.task().hold())) + " (since " + moments.minute(detail.task().hold().setAt()) + ")");
        }
        lines.addAll(deliveryLinesOf(detail));
        if (detail.decision() != null) {
            lines.add("");
            lines.addAll(decisionLines.of(detail.decision()));
        }
        if (!detail.task().notes().isEmpty()) {
            lines.add("");
            lines.add("Notes");
            lines.add(Words.indented(detail.task().notes(), INDENT));
        }
        if (!detail.stages().isEmpty()) {
            lines.add("");
            lines.add(stagesOf(detail.stages()));
        }
        lines.add("");
        lines.add(timelineOf(detail.timeline()));
        return lines.stream().collect(Collectors.joining("\n", "", "\n"));
    }

    private List<String> summaryOf(Task task) {
        Intake intake = task.intake();
        List<String> lines = new ArrayList<>();
        lines.add(task.id() + "  " + intake.kind().word() + "  " + task.stage().word());
        lines.add(intake.title());
        lines.add("Projects: " + TaskCells.projectsOf(task));
        if (intake.workKey() != null) {
            lines.add("Work key: " + intake.workKey());
        }
        if (intake.base() != null) {
            lines.add("Base: " + intake.base());
        }
        if (intake.delivery() != null) {
            lines.add("Delivery: " + intake.delivery().word());
        }
        lines.add("Since: " + moments.day(task.created()));
        if (task.isClosed()) {
            lines.add(capitalised(task.stage().word()) + ": " + moments.day(task.closed()));
        }
        return lines;
    }

    private static String workedLineOf(WorkedTime worked) {
        return "Worked: active " + hoursAndMinutesOf(worked.activeMinutes()) + " of " + hoursAndMinutesOf(worked.wallClockMinutes());
    }

    private static String hoursAndMinutesOf(long minutes) {
        return "%dh%02dm".formatted(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR);
    }

    private List<String> deliveryLinesOf(TaskDetail detail) {
        List<String> lines = new ArrayList<>();
        if (detail.pipelineRun() != null) {
            lines.add("Pipeline: run " + detail.pipelineRun().id() + " " + detail.pipelineRun().status()
                    + (detail.pipelineRun().activeStep() == null ? "" : " at " + detail.pipelineRun().activeStep()));
        }
        if (detail.delivery() != null) {
            Delivery delivery = detail.delivery();
            lines.add("Draft: " + delivery.address() + " " + delivery.state().word() + (delivery.draft() ? ", a draft" : ", not a draft")
                    + " (seen " + moments.minute(delivery.observedAt()) + ")");
        }
        if (detail.cost() != null) {
            lines.add("Cost: workers " + tokensOf(detail.cost().workers()) + "; pipeline "
                    + detail.cost().pipelineIfKnown().map(TaskDetailText::tokensOf).orElse("unknown"));
        }
        return lines;
    }

    private static String tokensOf(TokenUsage tokens) {
        return tokens.total() + " tokens (" + tokens.input() + " in, " + tokens.cacheRead() + " cache read, " + tokens.cacheWrite()
                + " cache write, " + tokens.output() + " out)";
    }

    private static String capitalised(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String stagesOf(List<StageReached> stages) {
        Table table = new Table(INDENT).line("Stages");
        stages.forEach(reached -> table.row(moments.minute(reached.at()), reached.stage().word(), reached.reason()));
        return table.text().stripTrailing();
    }

    private String timelineOf(List<RecordedEvent> timeline) {
        Table table = new Table(INDENT).line("Timeline");
        timeline.forEach(event -> table.row(moments.minute(event.at()), event.actor().name(), Words.ofType(event.type())));
        return table.text().stripTrailing();
    }
}
