package io.github.ythibos.edi.cli;

final class CoreUnavailable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    CoreUnavailable(String reason) {
        super(reason);
    }
}
