package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private static final String LIVE_WAIT = "The wake stream is live: edi wait --follow has run since 2026-10-26 20:31 CET.\n";
    private static final String NO_QUESTION = "No worker waits on an answer.\n";
    private static final String MISSING_WAIT = "No edi wait is live while 2 tasks are in flight: start edi wait --follow alone with the Monitor tool.\n";

    private static final String MEASURED = "The core's health since 2026-11-02 18:00 CET:\n  supervision round  2016 times  mean 41 ms  max 2.3 s\n";
    private static final String DUMPED = "The flight recording is written to /tmp/edi/data/recordings/core-20261109-180000.jfr.\n";

    private HttpServer core;
    private final List<String> asked = new ArrayList<>();
    private String primaryWait = LIVE_WAIT;
    private String openQuestions = NO_QUESTION;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @AfterEach
    void stopTheStubCore() {
        if (core != null) {
            core.stop(0);
        }
    }

    @Test
    void reportsACoreThatAnswersHealthyAndThePrimarysLiveWait() throws IOException {
        int port = startStubCoreAnswering(200);

        int exitCode = edi("health");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI core is up on 127.0.0.1:" + port + ".\n" + LIVE_WAIT + NO_QUESTION);
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void listsEachWorkerQuestionThatWaitsOnAnAnswerWithItsAge() throws IOException {
        openQuestions = "Workers waiting on an answer (1):\n  fit-lamp#1 (socket), 25 minutes: Which socket?\n";
        int port = startStubCoreAnswering(200);

        int exitCode = edi("health");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI core is up on 127.0.0.1:" + port + ".\n" + LIVE_WAIT + openQuestions);
    }

    @Test
    void saysSoWhenWorkIsInFlightAndNoWaitIsLive() throws IOException {
        primaryWait = MISSING_WAIT;
        int port = startStubCoreAnswering(200);

        int exitCode = edi("health");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI core is up on 127.0.0.1:" + port + ".\n" + MISSING_WAIT + NO_QUESTION);
    }

    @Test
    void namesWhyItCannotAskAboutTheWaitOfAHealthyCore() throws IOException {
        int port = startStubCoreAnswering(200);
        Files.delete(home.resolve("state/core.token"));

        int exitCode = edi("health");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI core is up on 127.0.0.1:" + port + ".\n");
        assertThat(err).hasToString("Cannot ask whether the primary's edi wait is live: EDI core is not running: "
                + home.resolve("state/core.token") + " holds no token.\n");
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
        assertThat(err).hasToString("EDI core is not running: " + home.resolve("state/core.port") + " records no port.\n");
    }

    @Test
    void reportsACoreThatHasNotFinishedRecordingItsPort() throws IOException {
        Files.createDirectories(home.resolve("state"));
        Files.writeString(home.resolve("state/core.port"), "");

        int exitCode = edi("health");

        assertThat(exitCode).isOne();
        assertThat(err).hasToString("EDI core is not running: " + home.resolve("state/core.port") + " records no port.\n");
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
        return new CommandLine(new Edi(Map.of("EDI_HOME", home.toString()), InputStream.nullInputStream()))
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(arguments);
    }

    @Test
    void addsTheCoresOwnHealthOverAPeriodAndWritesOutItsFlightRecording() throws IOException {
        int port = startStubCoreAnswering(200);

        int exitCode = edi("health", "--metrics", "--days", "30", "--dump-recording");

        assertThat(exitCode).isZero();
        assertThat(out).hasToString("EDI core is up on 127.0.0.1:" + port + ".\n" + LIVE_WAIT + NO_QUESTION + MEASURED + DUMPED);
        assertThat(asked).containsExactly("GET /api/telemetry?days=30", "POST /api/telemetry/recordings");
    }

    private int startStubCoreAnswering(int status) throws IOException {
        core = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        core.createContext("/api/health", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        core.createContext("/api/primary/wait", exchange -> {
            byte[] body = primaryWait.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        core.createContext("/api/workers/questions", exchange -> {
            byte[] body = openQuestions.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        core.createContext("/api/telemetry", exchange -> {
            asked.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            byte[] body = (exchange.getRequestMethod().equals("POST") ? DUMPED : MEASURED).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(exchange.getRequestMethod().equals("POST") ? 201 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        core.start();
        recordCorePort(core.getAddress().getPort());
        Files.writeString(home.resolve("state/core.token"), "home-token");
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
