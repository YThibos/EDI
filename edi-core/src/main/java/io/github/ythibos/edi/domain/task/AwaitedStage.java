package io.github.ythibos.edi.domain.task;

import java.util.Arrays;
import java.util.Locale;

public enum AwaitedStage {
    MERGED,
    LANDED;

    public static AwaitedStage named(String word) {
        return Arrays.stream(values())
                .filter(stage -> stage.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A task waits for another to be merged or landed, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String goal() {
        return switch (this) {
            case MERGED -> "to be merged";
            case LANDED -> "to land";
        };
    }

    public boolean isReachedBy(TaskStage stage) {
        return switch (this) {
            case MERGED -> stage == TaskStage.MERGED || stage.hasLanded();
            case LANDED -> stage.hasLanded();
        };
    }
}
