package io.github.ythibos.edi.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

final class StandInCore {

    static final String UP = "EDI stand-in core is up.";
    private static final int REFUSED = 1;

    private StandInCore() {
    }

    public static void main(String[] arguments) throws IOException {
        Duration stopping = Duration.ofMillis(Long.parseLong(arguments[0]));
        Path state = Path.of(System.getenv("EDI_HOME"), "state");
        Files.createDirectories(state);
        holdTheHome(state.resolve("core.pid"));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/health", exchange -> answer(exchange, ""));
        server.createContext("/api/core/stop", exchange -> {
            answer(exchange, "EDI core stops.\n");
            stopAfter(server, stopping);
        });
        server.start();
        Files.writeString(state.resolve("core.token"), StubCore.TOKEN + "\n");
        Files.writeString(state.resolve("core.port"), Integer.toString(server.getAddress().getPort()));
        System.out.println(UP);
    }

    private static void holdTheHome(Path pidFile) throws IOException {
        FileChannel held = FileChannel.open(pidFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        if (held.tryLock() == null) {
            System.out.println("Another EDI core runs on this home: it holds " + pidFile + ".");
            System.exit(REFUSED);
        }
        held.truncate(0).write(ByteBuffer.wrap((ProcessHandle.current().pid() + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private static void answer(HttpExchange exchange, String reply) throws IOException {
        byte[] body = reply.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }

    private static void stopAfter(HttpServer server, Duration stopping) {
        Thread.ofPlatform().start(() -> server.stop(0));
        Executors.newSingleThreadScheduledExecutor().schedule(() -> System.exit(0), stopping.toMillis(), TimeUnit.MILLISECONDS);
    }
}
