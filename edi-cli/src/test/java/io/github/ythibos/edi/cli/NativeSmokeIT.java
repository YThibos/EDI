package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("AT-44 the native core starts on a fresh store, answers its health and passes the command's contract")
class NativeSmokeIT {

    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final int ROUND_TRIPS = 200;

    @TempDir
    private static Path home;

    private static Process core;
    private static int port;

    @BeforeAll
    static void startTheNativeCore() throws IOException {
        ProcessBuilder launch = new ProcessBuilder(binary("edi.core.binary").toString())
                .redirectErrorStream(true)
                .redirectOutput(home.resolve("core.log").toFile());
        launch.environment().put("EDI_HOME", home.toString());
        launch.environment().put("SERVER_PORT", "0");
        core = launch.start();
        port = Integer.parseInt(awaitFile(home.resolve("state/core.port")).strip());
    }

    @AfterAll
    static void stopTheNativeCore() throws InterruptedException {
        if (core == null) {
            return;
        }
        core.destroy();
        if (!core.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
            core.descendants().forEach(ProcessHandle::destroyForcibly);
            core.destroyForcibly();
        }
    }

    @Test
    void theCoreAnswersItsHealthOnTheApiPath() throws Exception {
        HttpResponse<String> health = get("/api/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void theCoreKeepsAFreshStoreInItsHome() {
        assertThat(home.resolve("data/edi.db")).isRegularFile().isNotEmptyFile();
    }

    @Test
    void theCoreServesTheConsole() throws Exception {
        HttpResponse<String> console = get("/");

        assertThat(console.statusCode()).isEqualTo(200);
        assertThat(console.body()).contains("<app-root></app-root>");
    }

    @Test
    void theCommandReportsTheCoreHealthy() throws Exception {
        CommandRun health = edi("health");

        assertThat(health.exitCode()).isZero();
        assertThat(health.output()).isEqualTo("EDI core is up on 127.0.0.1:" + port + ".\n");
    }

    @Test
    void theCommandNamesItsVersion() throws Exception {
        CommandRun version = edi("--version");

        assertThat(version.exitCode()).isZero();
        assertThat(version.output()).matches("edi \\d+\\.\\d+\\.\\d+\\n");
    }

    @Test
    void theCommandRoundTripIsMeasured() throws Exception {
        List<Long> millis = new ArrayList<>();
        for (int call = 0; call < ROUND_TRIPS; call++) {
            long started = System.nanoTime();
            assertThat(edi("health").exitCode()).isZero();
            millis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
        Collections.sort(millis);

        Files.writeString(Path.of(System.getProperty("edi.smoke.report")), """
                ## Native smoke test

                `edi health` against the native core, %d round trips: p50 %d ms, p95 %d ms, slowest %d ms.
                """.formatted(ROUND_TRIPS, percentile(millis, 50), percentile(millis, 95), millis.getLast()));
    }

    private static Path binary(String property) {
        Path binary = Path.of(System.getProperty(property));
        assertThat(binary).as("native executable named by %s", property).isExecutable();
        return binary;
    }

    private static String awaitFile(Path file) throws IOException {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (!Files.exists(file)) {
            assertThat(core.isAlive()).as("the native core is running; its log:%n%s", log()).isTrue();
            assertThat(System.nanoTime()).as("the native core recorded its port in time").isLessThan(deadline);
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for " + file, interrupted);
            }
        }
        return Files.readString(file);
    }

    private static String log() throws IOException {
        return Files.readString(home.resolve("core.log"));
    }

    private static HttpResponse<String> get(String path) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(), BodyHandlers.ofString());
        }
    }

    private static CommandRun edi(String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of(binary("edi.cli.binary").toString()));
        commandLine.addAll(List.of(arguments));
        ProcessBuilder launch = new ProcessBuilder(commandLine).redirectErrorStream(true);
        launch.environment().put("EDI_HOME", home.toString());
        Process command = launch.start();
        String output = new String(command.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!command.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
            command.destroyForcibly();
        }
        return new CommandRun(command.exitValue(), output);
    }

    private static long percentile(List<Long> sorted, int percent) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * percent / 100.0) - 1));
    }

    private record CommandRun(int exitCode, String output) {
    }
}
