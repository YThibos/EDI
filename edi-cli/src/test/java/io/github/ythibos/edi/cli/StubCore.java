package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class StubCore implements AutoCloseable {

    static final String TOKEN = "stub-token";

    private final HttpServer server;
    private final Deque<Reply> queued = new ArrayDeque<>();
    private final List<Request> requests = new ArrayList<>();
    private int status = 200;
    private String reply = "";

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

    synchronized StubCore thenAnswering(int replyStatus, String replyBody) {
        queued.add(new Reply(replyStatus, replyBody));
        return this;
    }

    synchronized Request received() {
        return requests.isEmpty() ? null : requests.getLast();
    }

    synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        Request request = new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath() + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Accept"),
                exchange.getRequestHeaders().getFirst("EDI-Session"),
                exchange.getRequestHeaders().getFirst("EDI-Claude-Pid"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Reply answer = answerTo(request);
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private synchronized Reply answerTo(Request request) {
        requests.add(request);
        return queued.isEmpty() ? new Reply(status, reply) : queued.poll();
    }

    record Request(String method, String target, String authorization, String accepted, String session, String claudePid, String body) {
    }

    private record Reply(int status, String body) {
    }
}
