package io.github.ythibos.edi.cli;

import java.net.http.HttpHeaders;
import java.util.Optional;

record CoreReply(int status, String body, HttpHeaders headers) {

    private static final int FIRST_SUCCESS = 200;
    private static final int FIRST_FAILURE = 300;

    boolean succeeded() {
        return status >= FIRST_SUCCESS && status < FIRST_FAILURE;
    }

    Optional<String> header(String name) {
        return headers.firstValue(name);
    }
}
