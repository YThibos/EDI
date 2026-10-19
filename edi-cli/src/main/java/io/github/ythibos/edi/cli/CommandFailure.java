package io.github.ythibos.edi.cli;

final class CommandFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    CommandFailure(String reason) {
        super(reason);
    }
}
