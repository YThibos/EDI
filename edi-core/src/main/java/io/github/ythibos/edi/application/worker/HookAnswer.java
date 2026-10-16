package io.github.ythibos.edi.application.worker;

import java.util.Objects;

public record HookAnswer(Verdict verdict, String reason) {

    public static final HookAnswer PROCEED = new HookAnswer(Verdict.PROCEED, "");

    public HookAnswer {
        Objects.requireNonNull(verdict, "A hook answer gives its verdict.");
        reason = Objects.requireNonNullElse(reason, "");
        if (verdict != Verdict.PROCEED && reason.isBlank()) {
            throw new IllegalArgumentException("A hook that blocks or denies says why.");
        }
    }

    public static HookAnswer blockTheStop(String reason) {
        return new HookAnswer(Verdict.BLOCK_THE_STOP, reason);
    }

    public static HookAnswer denyTheTool(String reason) {
        return new HookAnswer(Verdict.DENY_THE_TOOL, reason);
    }

    public enum Verdict {
        PROCEED,
        BLOCK_THE_STOP,
        DENY_THE_TOOL
    }
}
