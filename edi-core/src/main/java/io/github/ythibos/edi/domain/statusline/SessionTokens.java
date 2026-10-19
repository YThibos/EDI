package io.github.ythibos.edi.domain.statusline;

import java.util.OptionalInt;

public record SessionTokens(long total, long cached, long uncached) {

    private static final int WHOLE = 100;

    public OptionalInt cacheHitRatio() {
        long read = cached + uncached;
        return read == 0 ? OptionalInt.empty() : OptionalInt.of((int) (cached * WHOLE / read));
    }
}
