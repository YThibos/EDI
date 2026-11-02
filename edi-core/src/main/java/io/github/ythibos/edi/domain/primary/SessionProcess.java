package io.github.ythibos.edi.domain.primary;

public record SessionProcess(long pid, Long startTick) {

    public SessionProcess {
        if (pid < 1) {
            throw new IllegalArgumentException("A process has a positive id, not " + pid + ".");
        }
    }

    public boolean isTheOneStartedAt(long observedStartTick) {
        return startTick == null || startTick == observedStartTick;
    }
}
