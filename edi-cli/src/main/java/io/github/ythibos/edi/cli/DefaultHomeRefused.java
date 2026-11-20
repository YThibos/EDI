package io.github.ythibos.edi.cli;

final class DefaultHomeRefused extends RuntimeException {

    private static final long serialVersionUID = 1L;

    DefaultHomeRefused(String reason) {
        super(reason);
    }
}
