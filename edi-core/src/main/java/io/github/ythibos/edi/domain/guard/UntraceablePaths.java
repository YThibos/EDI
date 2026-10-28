package io.github.ythibos.edi.domain.guard;

public class UntraceablePaths extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UntraceablePaths(String reason) {
        super(reason);
    }
}
