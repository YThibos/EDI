package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.ythibos.edi.testing.Patience;

final class StubCore implements AutoCloseable {

    static final String TOKEN = "stub-token";

    private final HttpServer server;
    private final Path portFile;
    private final Socket refusingPort = new Socket();
    private final Deque<Reply> queued = new ArrayDeque<>();
    private final List<Request> requests = new ArrayList<>();
    private int status = 200;
    private String reply = "";
    private int answeredBeforeDropping = Integer.MAX_VALUE;

    private StubCore(HttpServer server, Path portFile) {
        this.server = server;
        this.portFile = portFile;
    }

    static StubCore recordedIn(Path home) throws IOException {
        StubCore core = new StubCore(HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0), home.resolve("state/core.port"));
        core.server.createContext("/", core::answer);
        core.server.start();
        Files.createDirectories(home.resolve("state"));
        Files.writeString(core.portFile, Integer.toString(core.server.getAddress().getPort()));
        Files.writeString(home.resolve("state/core.token"), TOKEN + "\n");
        return core;
    }

    StubCore answering(int replyStatus, String replyBody) {
        this.status = replyStatus;
        this.reply = replyBody;
        return this;
    }

    synchronized StubCore thenAnswering(int replyStatus, String replyBody) {
        queued.add(new Reply(replyStatus, replyBody, null));
        return this;
    }

    synchronized StubCore thenWaking(long number, String digest) {
        queued.add(new Reply(200, digest, Long.toString(number)));
        return this;
    }

    synchronized StubCore droppingEveryCallAfter(int answered) {
        answeredBeforeDropping = answered;
        return this;
    }

    synchronized boolean awaitRequests(int count) throws InterruptedException {
        long giveUp = System.nanoTime() + Patience.FOR_REAL_WORK.toNanos();
        while (requests.size() < count && System.nanoTime() < giveUp) {
            wait(Math.max(1, (giveUp - System.nanoTime()) / 1_000_000));
        }
        return requests.size() >= count;
    }

    synchronized Request received() {
        return requests.isEmpty() ? null : requests.getLast();
    }

    synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    void stopAnswering() throws IOException {
        server.stop(0);
        refusingPort.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        Files.writeString(portFile, Integer.toString(refusingPort.getLocalPort()));
    }

    @Override
    public void close() {
        server.stop(0);
        try {
            refusingPort.close();
        } catch (IOException unclosable) {
            throw new UncheckedIOException(unclosable);
        }
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
        if (answer == null) {
            exchange.close();
            return;
        }
        if (answer.wakes() != null) {
            exchange.getResponseHeaders().add("EDI-Wakes", answer.wakes());
        }
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private synchronized Reply answerTo(Request request) {
        requests.add(request);
        notifyAll();
        if (requests.size() > answeredBeforeDropping) {
            return null;
        }
        return queued.isEmpty() ? new Reply(status, reply, null) : queued.poll();
    }

    record Request(String method, String target, String authorization, String accepted, String session, String claudePid, String body) {
    }

    private record Reply(int status, String body, String wakes) {
    }
}
