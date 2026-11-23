package io.github.ythibos.edi.application.task;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import io.github.ythibos.edi.domain.delivery.EffortYield;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;

public record WorkedWeek(LocalDate from, LocalDate thisWeek, ZoneId zone, List<LocalDate> days, List<WorkedTask> tasks, List<YieldAtAnEffort> efforts) {

    public WorkedWeek {
        days = List.copyOf(days);
        tasks = List.copyOf(tasks);
        efforts = List.copyOf(efforts);
    }

    public record WorkedTask(TaskId task, String title, WorkKey key, WorkKey bookedOn, List<WorkedDay> days) {

        public WorkedTask {
            days = List.copyOf(days);
        }
    }

    public record WorkedDay(LocalDate day, long activeMinutes, long wallClockMinutes) {
    }

    public record YieldAtAnEffort(String effort, int tasks, long weightedWorkerTokens, long weightedWorkerTokensPerTask, long weightedPipelineTokens,
            int reviewRounds, int findings, Double findingsPerReviewRound) {

        public static YieldAtAnEffort of(EffortYield yield) {
            return new YieldAtAnEffort(wordFor(yield.effort()), yield.tasks(), yield.workers().weighted(), yield.weightedWorkerTokensPerTask(),
                    yield.pipeline().weighted(), yield.reviewRounds(), yield.findings(),
                    yield.findingsPerReviewRound().isPresent() ? yield.findingsPerReviewRound().getAsDouble() : null);
        }

        private static String wordFor(EffortLevel effort) {
            return effort == null ? null : effort.word();
        }
    }
}
