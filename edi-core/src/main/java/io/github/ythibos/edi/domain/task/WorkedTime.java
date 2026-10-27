package io.github.ythibos.edi.domain.task;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public record WorkedTime(Duration active, Duration wallClock) {

    private static final Duration HALF_A_MINUTE = Duration.ofSeconds(30);

    public static Optional<WorkedTime> over(List<WorkMark> timeline, Instant until) {
        List<WorkMark> marks = marksInTimeOrderUntil(timeline, until);
        return firstDispatchIn(marks).map(start -> new WorkedTime(activeIn(marks, until), Duration.between(start, until)));
    }

    public long activeMinutes() {
        return nearestMinutesOf(active);
    }

    public long wallClockMinutes() {
        return nearestMinutesOf(wallClock);
    }

    private static List<WorkMark> marksInTimeOrderUntil(List<WorkMark> timeline, Instant until) {
        return timeline.stream().filter(mark -> !mark.at().isAfter(until)).sorted(Comparator.comparing(WorkMark::at)).toList();
    }

    private static Optional<Instant> firstDispatchIn(List<WorkMark> marks) {
        return marks.stream().filter(WorkMark::isDispatch).map(WorkMark::at).findFirst();
    }

    private static Duration activeIn(List<WorkMark> marks, Instant until) {
        return IntStream.range(0, marks.size())
                .filter(index -> marks.get(index).isWorking())
                .mapToObj(index -> Duration.between(marks.get(index).at(), nextMarkOrUntil(marks, index, until)))
                .reduce(Duration.ZERO, Duration::plus);
    }

    private static Instant nextMarkOrUntil(List<WorkMark> marks, int index, Instant until) {
        return index + 1 < marks.size() ? marks.get(index + 1).at() : until;
    }

    private static long nearestMinutesOf(Duration duration) {
        return duration.plus(HALF_A_MINUTE).toMinutes();
    }
}
