package io.github.ythibos.edi.testing;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import io.github.ythibos.edi.domain.task.CaptainsWord;
import io.github.ythibos.edi.domain.task.Holds;
import io.github.ythibos.edi.domain.task.StageTrigger;
import io.github.ythibos.edi.domain.task.Task;

public final class Stages {

    private static final StageTrigger.DoneReported DONE = new StageTrigger.DoneReported(List.of(), List.of(), List.of(), false, List.of(), true);

    private Stages() {
    }

    public static Task working(Task queued) {
        return moved(queued, new StageTrigger.Dispatch(Holds.none(), List.of(), true), queued.created());
    }

    public static Task delivered(Task working, Instant at) {
        return moved(working, DONE, at);
    }

    public static Task retired(Task queued, Instant at) {
        Task merged = moved(delivered(working(queued), at), new StageTrigger.RequestMerged("7e23dd8036558de2", List.of()), at);
        Task landed = moved(merged, new StageTrigger.LandingObserved(true, List.of()), at);
        return moved(landed, new StageTrigger.CleanUp(Set.of(), true), at);
    }

    public static Task answered(Task captainItem, Instant at) {
        return moved(captainItem.decisionAnswered(), new StageTrigger.CaptainItemAnswered(Holds.none()), at);
    }

    public static Task dropped(Task task, CaptainsWord word, Instant at) {
        return moved(task, new StageTrigger.Drop(word, List.of(), false), at);
    }

    private static Task moved(Task task, StageTrigger trigger, Instant at) {
        return task.movedBy(task.transitions().move(task.stage(), trigger), at);
    }
}
