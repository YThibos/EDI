package io.github.ythibos.edi.domain.worker;

import java.util.Arrays;
import java.util.Locale;

public enum NoticeKind {
    DIED,
    STALE,
    BUSY_TOO_LONG,
    WAIT_ENDED,
    LIMIT_RESUMED,
    LIMIT_HELD_BACK;

    public static NoticeKind named(String word) {
        return Arrays.stream(values())
                .filter(kind -> kind.word().equals(word))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("EDI notices a worker that died, went stale, stayed busy too long, ended its wait, or met its limit's reset, not " + word + "."));
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
