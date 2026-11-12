package io.github.ythibos.edi.cli;

import java.net.http.HttpHeaders;
import java.util.Optional;

record CoreReply(int status, String body, HttpHeaders headers) {

    private static final int FIRST_SUCCESS = 200;
    private static final int FIRST_FAILURE = 300;
    private static final int FIRST_REFUSAL = 400;
    private static final int NOT_FOUND = 404;
    private static final int FIRST_SERVER_FAILURE = 500;

    boolean succeeded() {
        return status >= FIRST_SUCCESS && status < FIRST_FAILURE;
    }

    boolean refusedUnderARule() {
        return status >= FIRST_REFUSAL && status < FIRST_SERVER_FAILURE && status != NOT_FOUND;
    }

    boolean failedInTheCore() {
        return status >= FIRST_SERVER_FAILURE;
    }

    Optional<String> header(String name) {
        return headers.firstValue(name);
    }
}
