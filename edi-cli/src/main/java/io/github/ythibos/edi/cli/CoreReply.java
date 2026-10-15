package io.github.ythibos.edi.cli;

record CoreReply(int status, String body) {

    private static final int FIRST_SUCCESS = 200;
    private static final int FIRST_FAILURE = 300;

    boolean succeeded() {
        return status >= FIRST_SUCCESS && status < FIRST_FAILURE;
    }
}
