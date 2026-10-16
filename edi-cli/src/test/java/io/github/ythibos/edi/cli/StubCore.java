package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class StubCore implements AutoCloseable {

    static final String TOKEN = "stub-token";

    private final HttpServer server;
    private int status = 200;
    private String reply = "";
    private Request received;

    private StubCore(HttpServer server) {
        this.server = server;
    }

    static StubCore recordedIn(Path home) throws IOException {
        StubCore core = new StubCore(HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
        core.server.createContext("/", core::answer);
        core.server.start();
        Files.createDirectories(home.resolve("state"));
        Files.writeString(home.resolve("state/core.port"), Integer.toString(core.server.getAddress().getPort()));
        Files.writeString(home.resolve("state/core.token"), TOKEN + "\n");
        return core;
    }

    StubCore answering(int replyStatus, String replyBody) {
        this.status = replyStatus;
        this.reply = replyBody;
        return this;
    }

    Request received() {
        return received;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        received = new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath() + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Accept"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = reply.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    record Request(String method, String target, String authorization, String accepted, String body) {
    }
}
