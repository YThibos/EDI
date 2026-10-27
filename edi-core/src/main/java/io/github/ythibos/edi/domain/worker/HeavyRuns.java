package io.github.ythibos.edi.domain.worker;

import java.util.List;
import java.util.Objects;

public record HeavyRuns(List<WorkerRun> runs) {

    public static final HeavyRuns NONE = new HeavyRuns(List.of());

    public HeavyRuns {
        runs = List.copyOf(runs);
    }

    public boolean runAtOnce() {
        return runs.size() > 1;
    }

    public List<WorkerRun> startedSince(HeavyRuns earlier) {
        return runs.stream().filter(run -> !earlier.runs.contains(run)).toList();
    }

    public List<WorkerRun> beside(WorkerRun run) {
        return runs.stream().filter(other -> !other.equals(run)).toList();
    }

    public record WorkerRun(WorkerId worker, HeavyRun run) {

        public WorkerRun {
            Objects.requireNonNull(worker, "A heavy run belongs to a worker.");
            Objects.requireNonNull(run, () -> worker + " runs a named kind of heavy run.");
        }
    }
}
