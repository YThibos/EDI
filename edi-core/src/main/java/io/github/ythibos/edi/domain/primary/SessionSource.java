package io.github.ythibos.edi.domain.primary;

public enum SessionSource {
    LAUNCH,
    RESTART,
    COMPACTION;

    public boolean isALaunch() {
        return this == LAUNCH;
    }

    public boolean keepsTheProcess() {
        return this == COMPACTION;
    }
}
