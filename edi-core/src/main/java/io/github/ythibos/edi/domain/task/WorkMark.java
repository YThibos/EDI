package io.github.ythibos.edi.domain.task;

import java.time.Instant;

import io.github.ythibos.edi.domain.status.StatusState;

public record WorkMark(Instant at, Kind kind) {

    public enum Kind {
        DISPATCHED,
        WORKING,
        STOPPED
    }

    public static WorkMark dispatched(Instant at) {
        return new WorkMark(at, Kind.DISPATCHED);
    }

    public static WorkMark reported(Instant at, StatusState state) {
        return countsAsWork(state) ? working(at) : stopped(at);
    }

    public static WorkMark working(Instant at) {
        return new WorkMark(at, Kind.WORKING);
    }

    public static WorkMark stopped(Instant at) {
        return new WorkMark(at, Kind.STOPPED);
    }

    public boolean isWorking() {
        return kind == Kind.WORKING;
    }

    public boolean isDispatch() {
        return kind == Kind.DISPATCHED;
    }

    private static boolean countsAsWork(StatusState state) {
        return state == StatusState.WORKING || state == StatusState.RESOLVED;
    }
}
