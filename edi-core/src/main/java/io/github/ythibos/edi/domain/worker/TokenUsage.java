package io.github.ythibos.edi.domain.worker;

public record TokenUsage(long input, long cacheRead, long cacheWrite, long output) {

    public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0);

    public TokenUsage {
        if (input < 0 || cacheRead < 0 || cacheWrite < 0 || output < 0) {
            throw new IllegalArgumentException("Token counts are never negative.");
        }
    }

    public TokenUsage plus(TokenUsage more) {
        return new TokenUsage(input + more.input, cacheRead + more.cacheRead, cacheWrite + more.cacheWrite, output + more.output);
    }

    public long total() {
        return input + cacheRead + cacheWrite + output;
    }
}
