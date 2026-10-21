package io.github.ythibos.edi.web.console;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

public final class ConsoleAddress {

    private ConsoleAddress() {
    }

    public static URI servedTo(HttpServletRequest request) {
        return URI.create("http://127.0.0.1:" + request.getLocalPort() + "/");
    }
}
