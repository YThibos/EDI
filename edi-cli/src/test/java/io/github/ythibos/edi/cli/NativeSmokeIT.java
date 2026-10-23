package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@Isolated
@DisplayName("AT-44 the native core starts on a fresh store, answers its health and passes the command's contract")
class NativeSmokeIT {

    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final RoundTripBudget ROUND_TRIP_BUDGET = new RoundTripBudget(200);
    private static final RoundTripBudget PROOF_BUDGET = new RoundTripBudget(50);
    private static final Duration MACHINE_SLOWDOWN = Duration.ofMillis(60);
    private static final String REGISTRY = """
            projects:
              - name: harness
                tracker: { kind: edi, projectKey: HRN }
                base: main
                delivery: local-branch
                captainCheckout: %s
            """;
    private static final String MACHINE_PROFILE = """
            workers:
              models:
                - id: claude-opus-5-5
                - id: claude-sonnet-5-5
                - id: claude-haiku-5-5
                  ignoresEffort: true
            """;
    private static final String STUB_SPEC = """
            stub-run: edi status working -- the stub worker started
            stub-run: printf 'stub\\n' > stub.txt && git add stub.txt && git -c user.name=Stub -c user.email=stub@example.org commit -q -m 'HRN-7 Add the stub file'
            stub-run: pkill -f a-pattern-the-guard-refuses
            stub-run: edi status done -- the stub worker finished
            """;
    private static final String WORKER_SOCKET = "edi-smoke-" + UUID.randomUUID();

    @RegisterExtension
    private static final TestProcesses processes = new TestProcesses();

    @TempDir
    private static Path home;

    private static Process core;
    private static int port;

    @BeforeAll
    static void startTheNativeCore() throws Exception {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), REGISTRY.formatted(home.resolve("checkouts/harness")));
        Files.writeString(home.resolve("config/machine.yaml"), MACHINE_PROFILE);
        Files.copy(Path.of(System.getProperty("edi.repository.root"), "examples/edi-home/config/dispatch-rules.json"),
                home.resolve("config/dispatch-rules.json"));
        cloneAProjectInto(home.resolve("projects/harness"));
        git(home, "clone", "--quiet", home.resolve("upstream/harness").toString(), home.resolve("checkouts/harness").toString());
        ProcessBuilder launch = new ProcessBuilder(binary("edi.core.binary").toString(),
                "--edi.workers.socket=" + WORKER_SOCKET,
                "--edi.workers.claude-code=" + stubClaudeCode(),
                "--edi.workers.claude-config=" + Files.createDirectories(home.resolve("claude-config")),
                "--edi.pipeline.home=" + pipelineHome(),
                "--edi.console.browser=" + stubBrowser(),
                "--edi.console.windows-directory=" + home)
                .redirectErrorStream(true)
                .redirectOutput(home.resolve("core.log").toFile());
        launch.environment().keySet().removeIf(variable -> variable.startsWith("GIT_CONFIG_") || variable.startsWith("TMUX") || variable.startsWith("EDI_"));
        launch.environment().put("PATH", binary("edi.cli.binary").getParent() + ":" + launch.environment().get("PATH"));
        launch.environment().put("EDI_HOME", home.toString());
        launch.environment().put("SERVER_PORT", "0");
        core = processes.register(launch.start());
        port = awaitRecordedAddress(home.resolve("state/core.port")).port();
    }

    @AfterAll
    static void stopTheNativeCore() throws Exception {
        stopTheWorkerServer();
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
    void theCommandOpensTheConsoleThroughALinkThatSignsInOnce() throws Exception {
        CommandRun opened = edi("", "console");
        String link = Files.readString(home.resolve("opened-in-the-browser")).strip().split(" ")[2];
        HttpResponse<String> signedIn = send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/console/session"))
                .header("X-EDI-Console", "1").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"token\":\"" + link.substring(link.indexOf('#') + 1) + "\"}")));
        String cookie = signedIn.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];

        assertThat(opened.output()).isEqualTo("EDI console: http://127.0.0.1:" + port + "/ is open in your browser.\n");
        assertThat(signedIn.statusCode()).isEqualTo(204);
        assertThat(send(withTheCookie("/api/sitrep", cookie).header("Accept", "application/json").GET()).body()).contains("\"schema\":\"edi-sitrep.v1\"");
        assertThat(get("/login").body()).contains("<app-root></app-root>");
        assertThat(openingLinesOfTheStream(cookie)).contains(":EDI fleet stream");
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

        assertThat(added.output()).isEqualTo("Queued native-lantern on HRN-3.\n");
        assertThat(backlog.output()).contains("native-lantern", "Native lantern");
        assertThat(shown.output()).contains("\"workKey\":\"HRN-3\"", "\"state\":\"queued\"");
    }

    @Test
    void theCommandRaisesAndAnswersADecision() throws Exception {
        edi("", "task", "add", "--kind", "captain", "--id", "native-choice", "--title", "Choose natively");
        CommandRun raised = edi("{\"title\":\"Which?\",\"context\":\"\",\"options\":[{\"key\":\"A\",\"text\":\"This\"}],\"recommend\":{\"key\":\"A\",\"why\":\"only one\"}}",
                "decision", "raise", "native-choice", "--card", "-");
        String number = raised.output().strip();
        Map<String, String> primary = Map.of("CLAUDE_CODE_SESSION_ID", UUID.randomUUID().toString());
        ediIn(Path.of(""), primary, "{\"session_id\":\"" + primary.get("CLAUDE_CODE_SESSION_ID") + "\"}", "hook", "session-start");
        ediIn(Path.of(""), primary, "{\"session_id\":\"" + primary.get("CLAUDE_CODE_SESSION_ID") + "\",\"prompt\":\"" + number + " A\"}",
                "hook", "user-prompt-submit");
        CommandRun answered = ediIn(Path.of(""), primary, "", "decision", "answer", number, "--option", "A", "--text", number + " A");

        assertThat(number).matches("D\\d+");
        assertThat(answered.output()).isEqualTo("Recorded the answer to " + number + " (option A); native-choice is done.\n");
        assertThat(edi("", "decision", "list", "--open").output()).doesNotContain(number + "  native-choice");
    }

    @Test
    void theCommandChecksTheMemoryAndFollowsThePresence() throws Exception {
        CommandRun memory = edi("", "memory", "check");
        CommandRun quiet = edi("", "presence", "quiet");
        CommandRun shown = edi("", "presence", "--json");
        CommandRun present = edi("", "presence", "present");

        assertThat(memory.exitCode()).isZero();
        assertThat(memory.output()).contains("Startup memory: 0 of 18000 tokens", "The memory check passes.");
        assertThat(quiet.output()).startsWith("Presence: quiet since ").endsWith(", on request.\n");
        assertThat(shown.output()).contains("\"mode\":\"quiet\"", "\"cause\":\"captain\"");
        assertThat(present.output()).isEqualTo("Presence: present.\n");
    }

    @Test
    void theCommandPromotesAScoutOnlyOnTheCaptainsWord() throws Exception {
        edi("", "task", "add", "--kind", "scout", "--id", "native-survey", "--project", "harness", "--title", "Survey natively", "--key", "HRN-9");
        Map<String, String> primary = Map.of("CLAUDE_CODE_SESSION_ID", UUID.randomUUID().toString());
        ediIn(Path.of(""), primary, "{\"session_id\":\"" + primary.get("CLAUDE_CODE_SESSION_ID") + "\"}", "hook", "session-start");
        ediIn(Path.of(""), primary, "{\"session_id\":\"" + primary.get("CLAUDE_CODE_SESSION_ID") + "\",\"prompt\":\"Ship the native survey.\"}",
                "hook", "user-prompt-submit");

        CommandRun refused = ediIn(Path.of(""), primary, "", "task", "promote", "native-survey");
        CommandRun heard = ediIn(Path.of(""), primary, "", "captain", "said", "--task", "native-survey", "--text", "Ship the native survey.");
        CommandRun promoted = ediIn(Path.of(""), primary, "", "task", "promote", "native-survey", "--title", "Ship natively");

        assertThat(refused.exitCode()).isOne();
        assertThat(refused.output()).contains("needs the captain's word on native-survey in this session");
        assertThat(heard.output()).isEqualTo("Recorded the captain's word on native-survey for this session: \"Ship the native survey.\"\n");
        assertThat(promoted.output()).isEqualTo("Promoted the scout native-survey to the ship ship-natively, queued on the captain's word.\n");
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
    void theCommandWritesTheStatusLineInAnyLocale() throws Exception {
        Path project = Files.createDirectories(home.resolve("statusline-project"));
        git(project, "init", "--quiet", "--initial-branch=main");
        Files.writeString(project.resolve("draft.txt"), "not yet added\n");
        String payload = """
                {"cwd":"%s","model":{"display_name":"Opus 5.5"},"effort":{"level":"high"},
                 "context_window":{"used_percentage":6,"total_input_tokens":58309,"total_output_tokens":105,
                  "current_usage":{"input_tokens":2,"cache_creation_input_tokens":33163,"cache_read_input_tokens":25144}},
                 "rate_limits":{"five_hour":{"used_percentage":28,"resets_at":1792361400}}}""".formatted(project);

        CommandRun line = ediInTheCLocale(payload, "statusline");

        assertThat(line.exitCode()).isZero();
        assertThat(line.output()).contains("\u001b[38;2;95;175;175mContext:\u001b[0m \u001b[38;2;54;129;40m6%\u001b[0m");
        assertThat(line.output().replaceAll("\u001b\\[[0-9;]*m", "")).matches("Opus 5\\.5 · high -- Context: 6% -- Session tokens: 58k \\(C 25k/!C 33k\\)"
                + " -- 5-hour: 28% \\([A-Z][a-z]{2} \\d{2}:\\d{2}\\) -- Week: -- -- Branch: main\\* -- Cache: 43%\n");
    }

    @Test
    void theCommandPlansTheMigrationOfAnOldHome() throws Exception {
        CommandRun plan = edi("", "migrate", "firstofficer", "--from", System.getProperty("edi.old.home.fixture"), "--dry-run");

        assertThat(plan.exitCode()).isZero();
        assertThat(plan.output()).contains("Tasks to import (17)", "the first new decision is D58", "For the captain (18)",
                "config/dispatch-rules.json  to replace");
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
    void aStubWorkerRunsItsBriefThroughTheHooksAndTakesASteerAtItsIdlePrompt() throws Exception {
        assumeTrue(answersIn(home, "tmux", "-V"), "tmux is not installed");
        edi("Fit the stub lamp; offline verification is accepted.", "task", "add", "--kind", "ship", "--project", "harness", "--id", "native-worker",
                "--title", "Native stub worker", "--key", "HRN-7", "--body", "-");

        CommandRun dispatched = edi(STUB_SPEC, "dispatch", "native-worker", "--category", "implementation", "--spec", "-");
        workerServer().ifPresent(server -> processes.register(server.pid()));
        CommandRun done = awaitOutput(List.of("worker", "list"), "done: the stub worker finished");
        awaitOutput(List.of("worker", "peek", "native-worker"), "shows idle-prompt");
        Path worktree = home.resolve("worktrees/harness/native-worker");
        CommandRun steered = edi("Look at the lamp again.", "steer", "native-worker");
        CommandRun acknowledged = awaitOutput(List.of("task", "show", "native-worker"), "steer acknowledged");

        assertThat(dispatched.output()).startsWith("Dispatched native-worker to native-worker#1 (implementation, claude-sonnet-5-5 at high) on feature/HRN-7");
        assertThat(home.resolve("no-mistakes/config.yaml")).content()
                .isEqualTo("agent: auto\n\nagent_args_override:\n  claude:\n    - --model\n    - claude-opus-5-5\n    - --effort\n    - high\n");
        assertThat(done.output()).contains("native-worker#1", "done: the stub worker finished");
        assertThat(gitOutput(worktree, "log", "--format=%s", "-1")).isEqualTo("HRN-7 Add the stub file\n");
        assertThat(steered.output()).isEqualTo("Message 1 waits for native-worker#1; its idle prompt was told to run edi inbox.\n");
        assertThat(acknowledged.output()).contains("task dispatched", "worker started", "status reported", "tool call refused", "steer sent",
                "steer received", "steer acknowledged");
        assertThat(edi("", "worker", "peek", "native-worker").output()).startsWith("native-worker#1 shows idle-prompt:\n");
        assertThat(edi("", "brief", "native-worker").output()).contains("# Brief: native-worker", "stub-run: edi status done");
        assertThat(acknowledged.output()).contains("branch delivered to captain");
        assertThat(gitOutput(home.resolve("checkouts/harness"), "rev-parse", "refs/heads/feature/HRN-7")).isEqualTo(gitOutput(worktree, "rev-parse", "HEAD"));
        CommandRun cleanup = edi("", "cleanup", "native-worker");
        assertThat(cleanup.exitCode()).isOne();
        assertThat(cleanup.output()).contains("native-worker is not cleaned up, because its work is not landed", "\"HRN-7 Add the stub file\" is not in origin/main");
        assertThat(worktree).isDirectory();
        stopTheWorkerServer();
    }

    @Test
    @DisplayName("AT-39 the native CLI round trip has a p95 under 50 ms, or a p50 under twice a bare start-up, over 200 calls or 20 seconds")
    void theCommandAnswersQuickly() throws Exception {
        Path command = binary("edi.cli.binary");

        RoundTripBudget.Measurement backlog = ROUND_TRIP_BUDGET.measure(backlogThrough(command), bareStartUpOf(command));

        Files.writeString(Path.of(System.getProperty("edi.smoke.report")), """
                ## Native smoke test

                `edi backlog` against the native core, %s
                """.formatted(backlog.summary()));
        assertThat(backlog.metTheBudget()).as(backlog.summary()).isTrue();
    }

    @Test
    void theRoundTripBudgetFailsACommandThatDoesItsWorkTwice() throws Exception {
        Path command = binary("edi.cli.binary");
        Path doubled = StandInScripts.installed(home.resolve("doubled-edi"),
                "#!/bin/sh\n'%s' \"$@\" > /dev/null && exec '%s' \"$@\"\n".formatted(command, command));

        RoundTripBudget.Measurement backlog = PROOF_BUDGET.measure(backlogThrough(doubled), bareStartUpOf(command));

        assertThat(backlog.metTheBudget()).as(backlog.summary()).isFalse();
    }

    @Test
    void theRoundTripBudgetKeepsPaceWithAMachineSlowedDown() throws Exception {
        Path slowed = StandInScripts.installed(home.resolve("slowed-edi"),
                "#!/bin/sh\nsleep %s\nexec '%s' \"$@\"\n".formatted(MACHINE_SLOWDOWN.toMillis() / 1000.0, binary("edi.cli.binary")));

        RoundTripBudget.Measurement backlog = PROOF_BUDGET.measure(backlogThrough(slowed), bareStartUpOf(slowed));

        assertThat(backlog.keptToTheLimit()).as(backlog.summary()).isFalse();
        assertThat(backlog.metTheBudget()).as(backlog.summary()).isTrue();
    }

    private static Path pipelineHome() throws IOException {
        Path pipelineHome = Files.createDirectories(home.resolve("no-mistakes"));
        Files.writeString(pipelineHome.resolve("config.yaml"), "agent: auto\n");
        return pipelineHome;
    }

    private static String stubBrowser() throws IOException {
        return StandInScripts.installed(home.resolve("browser"), "#!/bin/sh\nprintf '%s\\n' \"$*\" > '" + home.resolve("opened-in-the-browser") + "'\n")
                .toString();
    }

    private static String stubClaudeCode() throws IOException {
        try (InputStream script = NativeSmokeIT.class.getResourceAsStream("/edi-test-claude")) {
            return StandInScripts.installed(home.resolve("edi-test-claude"), new String(script.readAllBytes(), StandardCharsets.UTF_8)).toString();
        }
    }

    private static void cloneAProjectInto(Path clone) throws Exception {
        Path upstream = Files.createDirectories(home.resolve("upstream/harness"));
        git(upstream, "init", "--quiet", "--initial-branch=main");
        Files.writeString(upstream.resolve("README.md"), "harness\n");
        git(upstream, "add", "README.md");
        git(upstream, "commit", "--quiet", "--message", "Start harness");
        Files.createDirectories(clone.getParent());
        git(clone.getParent(), "clone", "--quiet", upstream.toString(), clone.toString());
    }

    private static CommandRun awaitOutput(List<String> command, String expected) throws Exception {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        CommandRun run = edi("", command.toArray(String[]::new));
        while (!run.output().contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(200);
            run = edi("", command.toArray(String[]::new));
        }
        return run;
    }

    private static String gitOutput(Path repository, String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of("git"));
        commandLine.addAll(List.of(arguments));
        return run(new ProcessBuilder(commandLine).directory(repository.toFile()).redirectErrorStream(true), "").output();
    }

    private static void stopTheWorkerServer() throws Exception {
        if (answersIn(home, "tmux", "-V")) {
            Optional<ProcessHandle> server = workerServer();
            run(new ProcessBuilder("tmux", "-L", WORKER_SOCKET, "kill-server").redirectErrorStream(true), "");
            server.ifPresent(TestProcesses::awaitExit);
            Files.deleteIfExists(Path.of(System.getenv().getOrDefault("TMUX_TMPDIR", "/tmp"),
                    "tmux-" + Files.getAttribute(Path.of("/proc/self"), "unix:uid"), WORKER_SOCKET));
        }
    }

    private static Optional<ProcessHandle> workerServer() throws Exception {
        CommandRun pid = run(new ProcessBuilder("tmux", "-L", WORKER_SOCKET, "display-message", "-p", "-t", "workers:", "#{pid}"), "");
        return pid.exitCode() == 0 ? ProcessHandle.of(Long.parseLong(pid.output().strip())) : Optional.empty();
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
        return send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)));
    }

    private static HttpRequest.Builder withTheCookie(String path, String cookie) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Cookie", cookie);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request.build(), BodyHandlers.ofString());
        }
    }

    private static List<String> openingLinesOfTheStream(String cookie) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        try {
            HttpResponse<Stream<String>> stream = http.sendAsync(withTheCookie("/api/stream", cookie).header("Accept", "text/event-stream")
                    .GET().build(), BodyHandlers.ofLines()).get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
            return CompletableFuture.supplyAsync(() -> stream.body().limit(2).toList()).get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
        } finally {
            http.shutdownNow();
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

    private static RoundTripBudget.RoundTrip backlogThrough(Path command) {
        return () -> assertThat(commandIn(command, Path.of(""), Map.of(), "", "backlog").exitCode()).isZero();
    }

    private static RoundTripBudget.RoundTrip bareStartUpOf(Path command) {
        return () -> assertThat(commandIn(command, Path.of(""), Map.of(), "", "--version").exitCode()).isZero();
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
        return commandIn(binary("edi.cli.binary"), directory, environment, input, arguments);
    }

    private static CommandRun commandIn(Path command, Path directory, Map<String, String> environment, String input, String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of(command.toString()));
        commandLine.addAll(List.of(arguments));
        ProcessBuilder launch = new ProcessBuilder(commandLine).directory(directory.toAbsolutePath().toFile()).redirectErrorStream(true);
        launch.environment().keySet().removeIf(variable -> variable.startsWith("TMUX") || variable.startsWith("CLAUDE") || variable.startsWith("EDI_"));
        launch.environment().putAll(environment);
        launch.environment().put("EDI_HOME", home.toString());
        return run(launch, input);
    }

    private static CommandRun run(ProcessBuilder launch, String input) throws Exception {
        Process command = processes.register(launch.start());
        try (OutputStream standardInput = command.getOutputStream()) {
            standardInput.write(input.getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(command.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!command.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
            command.destroyForcibly();
        }
        return new CommandRun(command.exitValue(), output);
    }

    private record CommandRun(int exitCode, String output) {
    }
}
