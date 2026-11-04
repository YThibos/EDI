package io.github.ythibos.edi.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorkedTimeTest {

    @Test
    void countsOnlyTheStretchesInWorkingAgainstTheWallClockSinceTheFirstDispatch() {
        List<WorkMark> timeline = List.of(
                WorkMark.dispatched(at("09:55:00")),
                WorkMark.working(at("10:00:00")),
                WorkMark.stopped(at("10:30:00")),
                WorkMark.working(at("11:00:00")));

        assertThat(WorkedTime.over(timeline, at("11:15:00")))
                .contains(new WorkedTime(Duration.ofMinutes(45), Duration.ofMinutes(80)));
    }

    @Test
    void runsAStretchAcrossRepeatedWorkingStatusesUntilTheNextStop() {
        List<WorkMark> timeline = List.of(
                WorkMark.dispatched(at("10:00:00")),
                WorkMark.working(at("10:00:00")),
                WorkMark.working(at("10:20:00")),
                WorkMark.stopped(at("10:40:00")),
                WorkMark.stopped(at("11:00:00")));

        assertThat(WorkedTime.over(timeline, at("12:00:00")).orElseThrow().activeMinutes()).isEqualTo(40);
    }

    @Test
    void endsAStretchAtAFreshDispatch() {
        List<WorkMark> timeline = List.of(
                WorkMark.dispatched(at("10:00:00")),
                WorkMark.working(at("10:05:00")),
                WorkMark.dispatched(at("10:35:00")),
                WorkMark.working(at("11:00:00")));

        assertThat(WorkedTime.over(timeline, at("11:10:00")).orElseThrow())
                .extracting(WorkedTime::activeMinutes, WorkedTime::wallClockMinutes)
                .containsExactly(40L, 70L);
    }

    @Test
    void ignoresWhatHappenedAfterTheMomentItMeasuresTo() {
        List<WorkMark> timeline = List.of(
                WorkMark.dispatched(at("10:00:00")),
                WorkMark.working(at("10:00:00")),
                WorkMark.stopped(at("12:00:00")));

        assertThat(WorkedTime.over(timeline, at("10:30:00")).orElseThrow().activeMinutes()).isEqualTo(30);
    }

    @Test
    void leavesOutTheDecisionsAndTheWaitsOfARealScoutsDay() {
        List<WorkMark> timeline = List.of(
                WorkMark.dispatched(at("11:54:57")),
                WorkMark.working(at("11:55:12")),
                WorkMark.stopped(at("12:23:31")),
                WorkMark.working(at("12:23:41")),
                WorkMark.working(at("12:29:25")),
                WorkMark.working(at("12:50:01")),
                WorkMark.stopped(at("13:00:55")),
                WorkMark.working(at("13:01:04")),
                WorkMark.working(at("13:22:59")),
                WorkMark.working(at("14:14:21")),
                WorkMark.stopped(at("14:14:25")),
                WorkMark.stopped(at("14:35:28")),
                WorkMark.stopped(at("15:01:41")));

        assertThat(WorkedTime.over(timeline, at("15:06:19")).orElseThrow())
                .extracting(WorkedTime::activeMinutes, WorkedTime::wallClockMinutes)
                .containsExactly(139L, 191L);
    }

    @Test
    void measuresTheMarksInTimeOrderWhenAStopIsLoggedAfterALaterStart() {
        List<WorkMark> timeline = List.of(
                WorkMark.dispatched(at("10:00:00")),
                WorkMark.working(at("10:00:00")),
                WorkMark.working(at("12:00:00")),
                WorkMark.stopped(at("10:30:00")));

        assertThat(WorkedTime.over(timeline, at("12:15:00")).orElseThrow().activeMinutes()).isEqualTo(45);
    }

    @Test
    void splitsAStretchAcrossMidnightIntoTwoDaysThatSumToTheWhole() {
        List<WorkMark> timeline = List.of(
                WorkMark.dispatched(Instant.parse("2026-10-26T22:50:00Z")),
                WorkMark.working(Instant.parse("2026-10-26T23:00:00Z")),
                WorkMark.stopped(Instant.parse("2026-10-27T00:40:00Z")));
        Instant until = Instant.parse("2026-10-27T01:00:00Z");
        Instant midnight = Instant.parse("2026-10-27T00:00:00Z");

        WorkedTime before = WorkedTime.within(timeline, until, Instant.parse("2026-10-26T00:00:00Z"), midnight).orElseThrow();
        WorkedTime after = WorkedTime.within(timeline, until, midnight, Instant.parse("2026-10-28T00:00:00Z")).orElseThrow();
        WorkedTime whole = WorkedTime.over(timeline, until).orElseThrow();

        assertThat(before).isEqualTo(new WorkedTime(Duration.ofMinutes(60), Duration.ofMinutes(70)));
        assertThat(after).isEqualTo(new WorkedTime(Duration.ofMinutes(40), Duration.ofMinutes(60)));
        assertThat(before.active().plus(after.active())).isEqualTo(whole.active());
        assertThat(before.wallClock().plus(after.wallClock())).isEqualTo(whole.wallClock());
    }

    @Test
    void measuresNothingInAWindowBeforeTheFirstDispatchOrAfterTheMomentItMeasuresTo() {
        List<WorkMark> timeline = List.of(WorkMark.dispatched(at("10:00:00")), WorkMark.working(at("10:00:00")));

        assertThat(WorkedTime.within(timeline, at("11:00:00"), at("08:00:00"), at("09:00:00")).orElseThrow().isNothing()).isTrue();
        assertThat(WorkedTime.within(timeline, at("11:00:00"), at("12:00:00"), at("13:00:00")).orElseThrow().isNothing()).isTrue();
        assertThat(WorkedTime.within(timeline, at("11:00:00"), at("10:30:00"), at("13:00:00")).orElseThrow().activeMinutes()).isEqualTo(30);
    }

    @Test
    void measuresNothingForATaskNeverDispatched() {
        assertThat(WorkedTime.over(List.of(WorkMark.stopped(at("10:00:00"))), at("11:00:00"))).isEmpty();
    }

    private static Instant at(String time) {
        return Instant.parse("2026-10-26T" + time + "Z");
    }
}
