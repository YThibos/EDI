package io.github.ythibos.edi.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.delivery.PipelineReview;
import io.github.ythibos.edi.domain.delivery.TaskCost;
import io.github.ythibos.edi.domain.dispatch.EffortLevel;
import io.github.ythibos.edi.domain.dispatch.ModelId;
import io.github.ythibos.edi.domain.dispatch.Route;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.TokenUsage;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;
import io.github.ythibos.edi.testing.WorkEvents;
import io.github.ythibos.edi.testing.WorkerFixtures;

class WeekOfWorkTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-10-12");
    private static final Route OPUS_HIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.HIGH);
    private static final Route OPUS_XHIGH = new Route(new ModelId("claude-opus-5-5"), EffortLevel.XHIGH);

    @TempDir
    private Path directory;

    private TestHome home;

    @BeforeEach
    void aLaptopWithATrackedShipAndAnEdiNativeShip() throws IOException {
        home = TrackerFixtures.laptopHome(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fix-lamps", "service", "SVC-40"), WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"),
                WorkerFixtures.queuedShip("chart-reef", "harness", "HRN-5")));
    }

    @Test
    void splitsEachTasksTimeAtMidnightIntoDaysThatSumToWhatTaskShowMeasures() {
        home.clock().advance(Duration.ofHours(14).plusMinutes(20));
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40")
                .after(Duration.ofMinutes(10))
                .reported(StatusState.WORKING, null, "SVC-40 started")
                .after(Duration.ofMinutes(60))
                .reported(StatusState.NEEDS_DECISION, "lamp-colour", "Which colour?")
                .after(Duration.ofMinutes(30))
                .reported(StatusState.WORKING, null, "SVC-40 painting")
                .after(Duration.ofMinutes(15))
                .reported(StatusState.DONE, null, "https://forge.example.org/team/service/merge_requests/7");

        WorkedWeek week = home.weekOfWork().from(MONDAY);

        WorkedWeek.WorkedTask lamps = week.tasks().getFirst();
        assertThat(week.days()).hasSize(7).startsWith(MONDAY).endsWith(LocalDate.parse("2026-10-18"));
        assertThat(lamps.days()).containsExactly(
                new WorkedWeek.WorkedDay(LocalDate.parse("2026-10-15"), 30, 40),
                new WorkedWeek.WorkedDay(LocalDate.parse("2026-10-16"), 45, 75));
        assertThat(lamps.days().stream().mapToLong(WorkedWeek.WorkedDay::activeMinutes).sum())
                .isEqualTo(home.workedTimes().of(new TaskId("fix-lamps"), home.clock().now()).orElseThrow().activeMinutes());
        assertThat(lamps.days().stream().mapToLong(WorkedWeek.WorkedDay::wallClockMinutes).sum())
                .isEqualTo(home.workedTimes().of(new TaskId("fix-lamps"), home.clock().now()).orElseThrow().wallClockMinutes());
    }

    @Test
    void showsAnEdiNativeKeyAgainstTheProfilesToolingTicketAndATrackedKeyAgainstItself() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40").reported(StatusState.WORKING, null, "SVC-40 started");
        WorkEvents.of(home, "fit-lamp").dispatchedTo(1, "feature/HRN-3").reported(StatusState.WORKING, null, "HRN-3 started").after(Duration.ofMinutes(20));

        WorkedWeek week = home.weekOfWork().from(MONDAY);

        assertThat(week.tasks()).extracting(WorkedWeek.WorkedTask::task, WorkedWeek.WorkedTask::key, WorkedWeek.WorkedTask::bookedOn).containsExactlyInAnyOrder(
                tuple(new TaskId("fix-lamps"), WorkKey.parse("SVC-40"), WorkKey.parse("SVC-40")),
                tuple(new TaskId("fit-lamp"), WorkKey.parse("HRN-3"), WorkKey.parse("SVC-2")));
    }

    @Test
    void startsThisWeekOnTheMondayOfTheMachinesDayAndNamesItOnEveryWeek() {
        WorkedWeek thisWeek = home.weekOfWork().thisWeek();
        WorkedWeek weekBefore = home.weekOfWork().from(MONDAY.minusWeeks(1));

        assertThat(thisWeek.from()).isEqualTo(MONDAY);
        assertThat(thisWeek.thisWeek()).isEqualTo(MONDAY);
        assertThat(weekBefore.thisWeek()).isEqualTo(MONDAY);
    }

    @Test
    void leavesOutATaskWithNoActiveTimeInTheWeek() {
        WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-40").reported(StatusState.WORKING, null, "SVC-40 started").after(Duration.ofMinutes(20));

        assertThat(home.weekOfWork().from(MONDAY.plusWeeks(1)).tasks()).isEmpty();
        assertThat(home.weekOfWork().from(MONDAY.minusWeeks(1)).tasks()).isEmpty();
    }

    @Test
    void showsTheCostAndReviewYieldPerEffortOfTheTasksCountedInTheWeek() {
        countedInTheWeek("fix-lamps", OPUS_HIGH, new TokenUsage(1_000_000, 10_000_000, 100_000, 50_000), new TokenUsage(200_000, 2_000_000, 0, 20_000),
                new PipelineReview(4, 6));
        countedInTheWeek("fit-lamp", OPUS_XHIGH, new TokenUsage(2_000_000, 20_000_000, 400_000, 200_000), TokenUsage.NONE, new PipelineReview(1, 0));
        home.taskCosts().save(new TaskCost(new TaskId("chart-reef"), OPUS_HIGH, new TokenUsage(9, 9, 9, 9), null,
                MONDAY.plusWeeks(1).atStartOfDay(home.clock().zone()).toInstant()));

        WorkedWeek week = home.weekOfWork().from(MONDAY);

        assertThat(week.efforts()).extracting(WorkedWeek.YieldAtAnEffort::effort, WorkedWeek.YieldAtAnEffort::tasks,
                WorkedWeek.YieldAtAnEffort::weightedWorkerTokens, WorkedWeek.YieldAtAnEffort::weightedPipelineTokens,
                WorkedWeek.YieldAtAnEffort::reviewRounds, WorkedWeek.YieldAtAnEffort::findings, WorkedWeek.YieldAtAnEffort::findingsPerReviewRound)
                .containsExactly(
                        tuple("high", 1, 2_375_000L, 500_000L, 4, 6, 1.5),
                        tuple("xhigh", 1, 5_500_000L, 0L, 1, 0, 0.0));
        assertThat(home.weekOfWork().from(MONDAY.plusWeeks(1)).efforts()).extracting(WorkedWeek.YieldAtAnEffort::workerTokens)
                .containsExactly(new TokenUsage(9, 9, 9, 9));
    }

    private void countedInTheWeek(String task, Route route, TokenUsage workers, TokenUsage pipeline, PipelineReview review) {
        home.taskCosts().save(new TaskCost(new TaskId(task), route, workers, pipeline, TestHome.START));
        home.reviews().save(new TaskId(task), review, TestHome.START);
    }
}
