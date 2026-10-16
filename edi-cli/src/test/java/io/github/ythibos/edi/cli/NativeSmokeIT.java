package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.OutputStream;
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
import java.util.Map;
import java.util.Optional;
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
    private static final long ROUND_TRIP_BUDGET_MILLISECONDS = 50;
    private static final String REGISTRY = """
            projects:
              - name: harness
                tracker: { kind: edi, projectKey: HRN }
            """;

    @TempDir
    private static Path home;

    private static Process core;
    private static int port;

    @BeforeAll
    static void startTheNativeCore() throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), REGISTRY);
        ProcessBuilder launch = new ProcessBuilder(binary("edi.core.binary").toString())
                .redirectErrorStream(true)
                .redirectOutput(home.resolve("core.log").toFile());
        launch.environment().put("EDI_HOME", home.toString());
        launch.environment().put("SERVER_PORT", "0");
        core = launch.start();
        port = awaitRecordedAddress(home.resolve("state/core.port")).port();
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
        CommandRun health = edi("", "health");

        assertThat(health.exitCode()).isZero();
        assertThat(health.output()).isEqualTo("EDI core is up on 127.0.0.1:" + port + ".\n");
    }

    @Test
    void theCommandNamesItsVersion() throws Exception {
        CommandRun version = edi("", "--version");

        assertThat(version.exitCode()).isZero();
        assertThat(version.output()).matches("edi \\d+\\.\\d+\\.\\d+\\n");
    }

    @Test
    void theCommandKeepsTheBacklog() throws Exception {
        CommandRun added = edi("", "task", "add", "--kind", "ship", "--project", "harness", "--title", "Native lantern", "--key", "HRN-3");
        CommandRun backlog = edi("", "backlog");
        CommandRun shown = edi("", "task", "show", "native-lantern", "--json");

        assertThat(added.output()).isEqualTo("Queued native-lantern.\n");
        assertThat(backlog.output()).contains("native-lantern", "Native lantern");
        assertThat(shown.output()).contains("\"workKey\":\"HRN-3\"", "\"state\":\"queued\"");
    }

    @Test
    void theCommandRaisesAndAnswersADecision() throws Exception {
        edi("", "task", "add", "--kind", "captain", "--id", "native-choice", "--title", "Choose natively");
        CommandRun raised = edi("{\"title\":\"Which?\",\"context\":\"\",\"options\":[{\"key\":\"A\",\"text\":\"This\"}],\"recommend\":{\"key\":\"A\",\"why\":\"only one\"}}",
                "decision", "raise", "native-choice", "--card", "-");
        String number = raised.output().strip();
        CommandRun answered = edi("", "decision", "answer", number, "--option", "A", "--text", number + " A");

        assertThat(number).matches("D\\d+");
        assertThat(answered.output()).isEqualTo("Recorded the answer to " + number + " (option A); native-choice is done.\n");
        assertThat(edi("", "decision", "list", "--open").output()).doesNotContain(number + "  native-choice");
    }

    @Test
    void theCommandRendersTheCaptainReportInAnyLocale() throws Exception {
        edi("", "task", "add", "--kind", "captain", "--id", "native-report-call", "--title", "Choose for the report");
        String number = edi("{\"title\":\"Which lantern?\",\"options\":[{\"key\":\"A\",\"text\":\"Brass\"}],\"recommend\":{\"key\":\"A\",\"why\":\"it shines\"}}",
                "decision", "raise", "native-report-call", "--card", "-").output().strip();

        CommandRun report = ediInTheCLocale("{\"done\":[{\"projects\":[\"harness\"],\"text\":\"Rendered natively\"}]}", "report", "--turn", "-");
        CommandRun sitrep = ediInTheCLocale("", "sitrep");
        CommandRun asJson = edi("", "report", "--json");

        assertThat(report.exitCode()).isZero();
        assertThat(report.output()).startsWith("╔══ NEEDS YOUR DECISION ══ ")
                .contains("║  %-6sWhich lantern?\n".formatted(number), "║        » Recommended: A - it shines\n", "  harness           • Rendered natively\n");
        assertThat(sitrep.output()).contains("║  %-6sWhich lantern?\n".formatted(number)).doesNotContain("Rendered natively");
        assertThat(asJson.output()).contains("\"fleet\":{", "\"width\":{\"columns\":120}");
    }

    @Test
    void theCommandPlansTheMigrationOfAnOldHome() throws Exception {
        CommandRun plan = edi("", "migrate", "firstofficer", "--from", System.getProperty("edi.old.home.fixture"), "--dry-run");

        assertThat(plan.exitCode()).isZero();
        assertThat(plan.output()).contains("Tasks to import (15)", "the first new decision is D58", "For the captain (8)");
    }

    @Test
    void theCommandRefusesInOnePlainSentence() throws Exception {
        CommandRun refused = edi("", "release", "no-such-task");

        assertThat(refused.exitCode()).isOne();
        assertThat(refused.output()).isEqualTo("No task is called no-such-task.\n");
    }

    @Test
    void theCommandReportsAPublishFindingAsJson() throws Exception {
        Path repository = repositoryWithAPersonalFileOn("share");
        assumeTrue(answersIn(repository, "gitleaks", "version"), "gitleaks is not installed");

        CommandRun check = ediIn(repository, "", "publish-check", "--range", "main..share", "--json");

        assertThat(check.exitCode()).isOne();
        assertThat(check.output()).contains("\"path\":\"personal/notes.md\"", "\"rule\":\"PERSONAL_PATH\"");
    }

    @Test
    @DisplayName("AT-39 the native CLI round trip has a p95 under 50 ms over 200 calls")
    void theCommandAnswersQuickly() throws Exception {
        List<Long> millis = new ArrayList<>();
        for (int call = 0; call < ROUND_TRIPS; call++) {
            long started = System.nanoTime();
            assertThat(edi("", "backlog").exitCode()).isZero();
            millis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
        Collections.sort(millis);

        Files.writeString(Path.of(System.getProperty("edi.smoke.report")), """
                ## Native smoke test

                `edi backlog` against the native core, %d round trips: p50 %d ms, p95 %d ms, slowest %d ms.
                """.formatted(ROUND_TRIPS, percentile(millis, 50), percentile(millis, 95), millis.getLast()));
        assertThat(percentile(millis, 95)).as("p95 of the round trip in milliseconds").isLessThan(ROUND_TRIP_BUDGET_MILLISECONDS);
    }

    private static Path binary(String property) {
        Path binary = Path.of(System.getProperty(property));
        assertThat(binary).as("native executable named by %s", property).isExecutable();
        return binary;
    }

    private static CoreAddress awaitRecordedAddress(Path portFile) throws IOException {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        Optional<CoreAddress> recorded = CoreAddress.recordedIn(portFile);
        while (recorded.isEmpty()) {
            assertThat(core.isAlive()).as("the native core is running; its log:%n%s", log()).isTrue();
            assertThat(System.nanoTime()).as("the native core recorded its port in time").isLessThan(deadline);
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for " + portFile, interrupted);
            }
            recorded = CoreAddress.recordedIn(portFile);
        }
        return recorded.get();
    }

    private static String log() throws IOException {
        return Files.readString(home.resolve("core.log"));
    }

    private static HttpResponse<String> get(String path) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(), BodyHandlers.ofString());
        }
    }

    private static Path repositoryWithAPersonalFileOn(String branch) throws Exception {
        Path repository = Files.createDirectories(home.resolve("repository"));
        git(repository, "init", "--quiet", "--initial-branch=main");
        git(repository, "commit", "--quiet", "--allow-empty", "--message", "Start");
        git(repository, "switch", "--quiet", "--create", branch);
        Files.createDirectories(repository.resolve("personal"));
        Files.writeString(repository.resolve("personal/notes.md"), "Private notes.\n");
        git(repository, "add", "personal/notes.md");
        git(repository, "commit", "--quiet", "--message", "Share the notes");
        return repository;
    }

    private static void git(Path repository, String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of("git", "-c", "user.name=Smoke", "-c", "user.email=smoke@example.org"));
        commandLine.addAll(List.of(arguments));
        ProcessBuilder launch = new ProcessBuilder(commandLine).directory(repository.toFile()).redirectErrorStream(true);
        launch.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        launch.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        assertThat(run(launch, "").exitCode()).as("git %s", String.join(" ", arguments)).isZero();
    }

    private static boolean answersIn(Path directory, String... versionCommand) {
        try {
            return run(new ProcessBuilder(versionCommand).directory(directory.toFile()).redirectErrorStream(true), "").exitCode() == 0;
        } catch (Exception missing) {
            return false;
        }
    }

    private static CommandRun edi(String input, String... arguments) throws Exception {
        return ediIn(Path.of(""), input, arguments);
    }

    private static CommandRun ediInTheCLocale(String input, String... arguments) throws Exception {
        return ediIn(Path.of(""), Map.of("LANG", "C", "LC_ALL", "C"), input, arguments);
    }

    private static CommandRun ediIn(Path directory, String input, String... arguments) throws Exception {
        return ediIn(directory, Map.of(), input, arguments);
    }

    private static CommandRun ediIn(Path directory, Map<String, String> environment, String input, String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of(binary("edi.cli.binary").toString()));
        commandLine.addAll(List.of(arguments));
        ProcessBuilder launch = new ProcessBuilder(commandLine).directory(directory.toAbsolutePath().toFile()).redirectErrorStream(true);
        launch.environment().keySet().removeAll(List.of("TMUX", "TMUX_PANE"));
        launch.environment().putAll(environment);
        launch.environment().put("EDI_HOME", home.toString());
        return run(launch, input);
    }

    private static CommandRun run(ProcessBuilder launch, String input) throws Exception {
        Process command = launch.start();
        try (OutputStream standardInput = command.getOutputStream()) {
            standardInput.write(input.getBytes(StandardCharsets.UTF_8));
        }
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
