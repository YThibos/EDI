package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import picocli.CommandLine;

@Tag("adapter")
class HealthCommandTest {

    @TempDir
    private Path home;

    private HttpServer core;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @AfterEach
    void stopTheStubCore() {
        if (core != null) {
            core.stop(0);
        }
    }

    @Test
    void reportsACoreThatAnswersHealthy() throws IOException {
        int port = startStubCoreAnswering(200);

        int exitCode = edi("health");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI core is up on 127.0.0.1:" + port + ".\n");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void reportsACoreThatAnswersUnhealthy() throws IOException {
        int port = startStubCoreAnswering(503);

        int exitCode = edi("health");

        assertThat(exitCode).isOne();
        assertThat(err).hasToString("EDI core on 127.0.0.1:" + port + " reports that it is not healthy.\n");
    }

    @Test
    void reportsACoreThatRecordedAPortButDoesNotAnswer() throws IOException {
        int port = aPortNobodyListensOn();
        recordCorePort(port);

        int exitCode = edi("health");

        assertThat(exitCode).isOne();
        assertThat(err).hasToString("EDI core is not answering on 127.0.0.1:" + port + ".\n");
    }

    @Test
    void reportsACoreThatNeverRecordedAPort() {
        int exitCode = edi("health");

        assertThat(exitCode).isOne();
        assertThat(err).hasToString("EDI core is not running: " + home.resolve("state/core.port") + " does not exist.\n");
    }

    @Test
    void namesTheVersionItWasBuiltAs() {
        int exitCode = edi("--version");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).matches("edi \\d+\\.\\d+\\.\\d+\\n");
    }

    @Test
    void asksForACommandWhenNoneIsNamed() {
        int exitCode = edi();

        assertThat(exitCode).isEqualTo(2);
        assertThat(err.toString()).startsWith("Name a command; edi --help lists them.");
    }

    private int edi(String... arguments) {
        return new CommandLine(new Edi(Map.of("EDI_HOME", home.toString())))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }

    private int startStubCoreAnswering(int status) throws IOException {
        core = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        core.createContext("/api/health", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        core.start();
        recordCorePort(core.getAddress().getPort());
        return core.getAddress().getPort();
    }

    private void recordCorePort(int port) throws IOException {
        Files.createDirectories(home.resolve("state"));
        Files.writeString(home.resolve("state/core.port"), Integer.toString(port));
    }

    private static int aPortNobodyListensOn() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
