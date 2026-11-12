package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.Patience;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@DisplayName("The native core supervises the primary and recovers workers after a simulated machine restart")
class NativeRecoveryIT {

    private static final Duration OWN_WORK = Duration.ofSeconds(30);
    private static final String REGISTRY = """
            projects:
              - name: harness
                tracker: { kind: edi, projectKey: HRN }
                base: main
            """;
    private static final String MACHINE_PROFILE = """
            workers:
              models:
                - id: claude-opus-5-5
                - id: claude-sonnet-5-5
                - id: claude-haiku-5-5
                  ignoresEffort: true
            claude:
              logins:
                default: { configDirectory: %s }
            """;
    private static final String FIRST_RUN_FINISHED = "{\"hasCompletedOnboarding\": true}";
    private static final String WORKER_SOCKET = "edi-recovery-" + UUID.randomUUID();
    private static final Pattern FIRST_SESSION = Pattern.compile("--session-id (\\S+)");

    @RegisterExtension
    private static final TestProcesses processes = new TestProcesses();

    @TempDir
    private static Path homes;

    private static Path home;
    private static Process core;
    private static long ownWorkStarted;
    private static long startUpsWaitedFor;

    @BeforeEach
    void startTheNativeCoreInAFreshHome() throws Exception {
        home = Files.createTempDirectory(homes, "home-");
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), REGISTRY);
        Files.writeString(home.resolve("config/machine.yaml"), MACHINE_PROFILE.formatted(claudeLogin()));
        Files.writeString(claudeLogin().resolve(".claude.json"), FIRST_RUN_FINISHED);
        Files.copy(Path.of(System.getProperty("edi.repository.root"), "examples/edi-home/config/dispatch-rules.json"),
                home.resolve("config/dispatch-rules.json"));
        Files.writeString(bootIdFile(), UUID.randomUUID().toString());
        cloneAProjectInto(home.resolve("projects/harness"));
        core = startedCore();
        ownWorkStarted = System.nanoTime();
        startUpsWaitedFor = 0;
    }

    @AfterEach
    void stopTheNativeCore() throws Exception {
        Duration ownWork = Duration.ofNanos(System.nanoTime() - ownWorkStarted - startUpsWaitedFor);
        stopTheWorkerServer();
        stop(core);
        assertThat(ownWork).as("the test's own work, without the native core's start-ups it waited for").isLessThan(OWN_WORK);
    }

    @Test
    void aMachineRestartPutsTheLostWorkersOnTheNumberedListAndResumesOnlyTheNamedNumber() throws Exception {
        assumeTrue(answers("tmux", "-V"), "tmux is not installed");
        dispatch("restart-lamp", "HRN-11", "stub-run: edi status working -- the lamp worker started");
        dispatch("restart-horn", "HRN-12", "stub-run: edi status working -- the horn worker started");
        awaitOutput(List.of("worker", "list"), "the horn worker started");

        simulateAMachineRestart();
        CommandRun listed = edi(Map.of(), "", "recover");
        CommandRun resumed = edi(Map.of(), "", "recover", "2", "resume");
        workerServer().ifPresent(server -> processes.register(server.pid()));
        List<String> hornLaunches = awaitLaunches("restart-horn", 2);
        CommandRun remaining = edi(Map.of(), "", "recover");
        CommandRun workers = edi(Map.of(), "", "worker", "list");

        assertThat(listed.output()).contains("Outage hold list (2)", "1   restart-lamp  harness  lost when the machine restarted",
                "2   restart-horn  harness  lost when the machine restarted", "outcome so far: working: the horn worker started",
                "ran on claude-sonnet-5-5 at high; its category gives claude-sonnet-5-5 at high now");
        assertThat(resumed.output()).startsWith("Relaunched restart-horn as restart-horn#2 (implementation, claude-sonnet-5-5 at high), resuming session ");
        assertThat(hornLaunches.get(1)).contains("--resume " + sessionOf(hornLaunches.getFirst()));
        assertThat(launchesOf("restart-lamp")).hasSize(1);
        assertThat(remaining.output()).contains("Outage hold list (1)", "1   restart-lamp").doesNotContain("restart-horn  harness");
        assertThat(workers.output()).contains("restart-lamp#1  dead: the machine restarted").doesNotContain("restart-horn#1");
    }

    @Test
    void aResumeOfASessionThatLeftNoTranscriptStartsAfreshWithAHandoverInstead() throws Exception {
        assumeTrue(answers("tmux", "-V"), "tmux is not installed");
        dispatch("restart-buoy", "HRN-16", "stub-run: edi status working -- the buoy worker started");
        awaitOutput(List.of("worker", "list"), "the buoy worker started");
        String session = sessionOf(launchesOf("restart-buoy").getFirst());

        simulateAMachineRestart();
        deleteTheTranscriptOf(session);
        edi(Map.of(), "", "recover");
        CommandRun relaunched = edi(Map.of(), "", "recover", "1", "resume");
        workerServer().ifPresent(server -> processes.register(server.pid()));
        List<String> launches = awaitLaunches("restart-buoy", 2);
        CommandRun workers = awaitOutput(List.of("worker", "list"), "restart-buoy#2");

        assertThat(relaunched.output()).startsWith("Relaunched restart-buoy as restart-buoy#2 (implementation, claude-sonnet-5-5 at high), with a fresh"
                + " session and a handover because session " + session + " left no transcript to resume, in ");
        assertThat(launches).hasSize(2);
        assertThat(sessionOf(launches.get(1))).isNotEqualTo(session);
        assertThat(launches.get(1)).doesNotContain("--resume")
                .contains(" and session " + session + " left no transcript to resume: read the handover " + home.resolve("data/tasks/restart-buoy/handover.md"));
        assertThat(workers.output()).contains("restart-buoy#2").doesNotContain("restart-buoy#2  dead");
    }

    @Test
    void aWorkerWhoseContextFillsWakesThePrimaryAtOnceAndStartsAfreshWithAHandover() throws Exception {
        assumeTrue(answers("tmux", "-V"), "tmux is not installed");
        stop(core);
        core = startedCore("--edi.supervision.round=PT1S");
        String session = UUID.randomUUID().toString();
        Map<String, String> primary = Map.of("CLAUDE_CODE_SESSION_ID", session);
        edi(primary, payload(session, "\"source\":\"startup\""), "hook", "session-start");
        dispatch("fill-lamp", "HRN-15", "stub-run: echo brass > lamp.txt\nstub-run: edi status working -- commit 1 of 2 done\nstub-context: full");

        CommandRun full = awaitOutput(List.of("worker", "list"), "fill-lamp#1  context full since");
        CommandRun woken = awaitTheWake(primary, "fill-lamp#1's context has been full since");
        CommandRun refused = edi(Map.of(), "", "worker", "relaunch", "fill-lamp");
        CommandRun stopped = edi(Map.of(), "", "worker", "stop", "fill-lamp");
        CommandRun relaunched = edi(Map.of(), "", "worker", "relaunch", "fill-lamp");
        workerServer().ifPresent(server -> processes.register(server.pid()));
        List<String> launches = awaitLaunches("fill-lamp", 2);
        Path handover = home.resolve("data/tasks/fill-lamp/handover.md");

        assertThat(full.output()).contains("working: commit 1 of 2 done");
        assertThat(woken.output()).contains("edi worker stop fill-lamp, then edi worker relaunch fill-lamp starts a fresh session in its copy with a handover.");
        assertThat(refused.output()).contains("fill-lamp#1's context is full and its session still runs; stop it first with edi worker stop");
        assertThat(stopped.output()).contains("edi worker relaunch fill-lamp starts a fresh session with a handover, since its context is full.");
        assertThat(relaunched.output()).startsWith("Relaunched fill-lamp as fill-lamp#2 (implementation, claude-sonnet-5-5 at high), with a fresh session and a handover");
        assertThat(launches).hasSize(2);
        assertThat(launches.get(1)).doesNotContain("--resume").doesNotContain(sessionOf(launches.getFirst()))
                .contains("fresh start after its context filled up: read the handover " + handover);
        assertThat(handover).content().contains("fill-lamp#2 takes over from fill-lamp#1 in the same isolated copy, because its context filled up.",
                "No commits beyond origin/main yet.", "Uncommitted changes:\n\n- lamp.txt\n", "working: commit 1 of 2 done");
    }

    @Test
    void thePrimaryEndsAStateChangingTurnWithTheReportAndEdiWaitPrintsWhatNeedsIt() throws Exception {
        assumeTrue(answers("tmux", "-V"), "tmux is not installed");
        String session = UUID.randomUUID().toString();
        Map<String, String> primary = Map.of("CLAUDE_CODE_SESSION_ID", session);

        CommandRun started = edi(primary, payload(session, "\"source\":\"startup\""), "hook", "session-start");
        edi(primary, payload(session, "\"prompt\":\"Queue the bell.\""), "hook", "user-prompt-submit");
        edi(primary, "", "task", "add", "--kind", "ship", "--project", "harness", "--title", "Ring the bell", "--key", "HRN-13");
        CommandRun unreported = edi(primary, payload(session, "\"stop_hook_active\":false"), "hook", "stop");
        edi(primary, "", "report");
        CommandRun reported = edi(primary, payload(session, "\"stop_hook_active\":false"), "hook", "stop");
        dispatch("wake-bell", "HRN-14", "stub-run: edi status needs-decision --key bell -- Brass or bronze?");
        workerServer().ifPresent(server -> processes.register(server.pid()));
        CommandRun woken = awaitTheWake(primary, "wake-bell#1 needs a decision: (bell) Brass or bronze?");

        assertThat(started.output()).contains("\"additionalContext\":\"EDI session-start digest\\nThis session is the primary",
                "The console's sitrep page is open in the captain's browser at http://127.0.0.1:");
        assertThat(home.resolve("opened-in-the-browser")).content().startsWith("/c start http://127.0.0.1:").contains("/hand-off/");
        assertThat(unreported.output()).contains("\"decision\":\"block\"", "render the turn report with edi report --turn -");
        assertThat(reported.exitCode()).isZero();
        assertThat(reported.output()).isEmpty();
        assertThat(woken.exitCode()).isZero();
        assertThat(woken.output()).startsWith("EDI wake: ").contains("wake-bell#1 needs a decision: (bell) Brass or bronze?");
    }

    private static Process startedCore(String... options) throws Exception {
        Files.deleteIfExists(home.resolve("state/core.port"));
        List<String> commandLine = new ArrayList<>(List.of(binary("edi.core.binary").toString(),
                "--edi.workers.socket=" + WORKER_SOCKET,
                "--edi.workers.claude-code=" + stubClaudeCode(),
                "--edi.workers.mise=" + stubMise(),
                "--edi.pipeline.home=" + home.resolve("no-mistakes"),
                "--edi.machine.boot-id=" + bootIdFile(),
                "--edi.console.browser=" + stubBrowser(),
                "--edi.console.windows-directory=" + home));
        commandLine.addAll(List.of(options));
        ProcessBuilder launch = new ProcessBuilder(commandLine)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(home.resolve("core.log").toFile()));
        launch.environment().keySet().removeIf(variable -> variable.startsWith("GIT_CONFIG_") || variable.startsWith("TMUX") || variable.startsWith("EDI_")
                || variable.startsWith("CLAUDE"));
        launch.environment().put("PATH", binary("edi.cli.binary").getParent() + ":" + launch.environment().get("PATH"));
        launch.environment().put("EDI_HOME", home.toString());
        launch.environment().put("SERVER_PORT", "0");
        Process started = processes.register(launch.start());
        long startUp = System.nanoTime();
        awaitThePortOf(started);
        startUpsWaitedFor += System.nanoTime() - startUp;
        return started;
    }

    private static void simulateAMachineRestart() throws Exception {
        stop(core);
        stopTheWorkerServer();
        Files.writeString(bootIdFile(), UUID.randomUUID().toString());
        core = startedCore();
    }

    private static void deleteTheTranscriptOf(String session) throws IOException {
        try (Stream<Path> files = Files.walk(claudeLogin().resolve("projects"))) {
            List<Path> transcripts = files.filter(file -> file.getFileName().toString().equals(session + ".jsonl")).toList();
            assertThat(transcripts).as("the transcript of session %s", session).isNotEmpty();
            for (Path transcript : transcripts) {
                Files.delete(transcript);
            }
        }
    }

    private static void dispatch(String task, String key, String spec) throws Exception {
        edi(Map.of(), "", "task", "add", "--kind", "ship", "--project", "harness", "--id", task, "--title", "Work on " + task, "--key", key);
        CommandRun dispatched = edi(Map.of(), spec + "\n", "dispatch", task, "--category", "implementation", "--spec", "-");
        assertThat(dispatched.output()).as("the dispatch of %s", task).startsWith("Dispatched " + task);
        workerServer().ifPresent(server -> processes.register(server.pid()));
    }

    private static CommandRun awaitTheWake(Map<String, String> primary, String expected) throws Exception {
        CommandRun woken = edi(primary, "", "wait");
        for (int earlierWakes = 0; earlierWakes < 5 && woken.exitCode() == 0 && !woken.output().contains(expected); earlierWakes++) {
            woken = edi(primary, "", "wait");
        }
        return woken;
    }

    private static String payload(String session, String fields) {
        return "{\"session_id\":\"" + session + "\",\"hook_event_name\":\"hook\"," + fields + "}";
    }

    private static List<String> awaitLaunches(String task, int count) throws Exception {
        long deadline = System.nanoTime() + Patience.FOR_REAL_WORK.toNanos();
        List<String> launches = launchesOf(task);
        while (launches.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(100);
            launches = launchesOf(task);
        }
        return launches;
    }

    private static List<String> launchesOf(String task) throws IOException {
        Path record = home.resolve("data/tasks/" + task + "/stub-launches.txt");
        return Files.exists(record) ? Files.readAllLines(record) : List.of();
    }

    private static String sessionOf(String launch) {
        Matcher session = FIRST_SESSION.matcher(launch);
        assertThat(session.find()).as("the first launch names its session: %s", launch).isTrue();
        return session.group(1);
    }

    private static CommandRun awaitOutput(List<String> command, String expected) throws Exception {
        long deadline = System.nanoTime() + Patience.FOR_REAL_WORK.toNanos();
        CommandRun run = edi(Map.of(), "", command.toArray(String[]::new));
        while (!run.output().contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            run = edi(Map.of(), "", command.toArray(String[]::new));
        }
        return run;
    }

    private static String stubClaudeCode() throws IOException {
        try (InputStream script = NativeRecoveryIT.class.getResourceAsStream("/edi-test-claude")) {
            return StandInScripts.installed(home.resolve("edi-test-claude"), new String(script.readAllBytes(), StandardCharsets.UTF_8)).toString();
        }
    }

    private static String stubMise() throws IOException {
        try (InputStream script = NativeRecoveryIT.class.getResourceAsStream("/edi-test-mise")) {
            return StandInScripts.installed(home.resolve("edi-test-mise"), new String(script.readAllBytes(), StandardCharsets.UTF_8)).toString();
        }
    }

    private static Path claudeLogin() throws IOException {
        return Files.createDirectories(home.resolve("claude-config"));
    }

    private static Path bootIdFile() {
        return home.resolve("boot_id");
    }

    private static String stubBrowser() throws IOException {
        return StandInScripts.installed(home.resolve("browser"), "#!/bin/sh\nprintf '%s\\n' \"$*\" > '" + home.resolve("opened-in-the-browser") + "'\n")
                .toString();
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

    private static void git(Path repository, String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of("git", "-c", "user.name=Recovery", "-c", "user.email=recovery@example.org"));
        commandLine.addAll(List.of(arguments));
        ProcessBuilder launch = new ProcessBuilder(commandLine).directory(repository.toFile()).redirectErrorStream(true);
        launch.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        launch.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        assertThat(run(launch, "").exitCode()).as("git %s", String.join(" ", arguments)).isZero();
    }

    private static void awaitThePortOf(Process started) throws Exception {
        long deadline = System.nanoTime() + Patience.FOR_REAL_WORK.toNanos();
        while (CoreAddress.recordedIn(home.resolve("state/core.port")).isEmpty()) {
            assertThat(started.isAlive()).as("the native core is running; its log:%n%s", Files.readString(home.resolve("core.log"))).isTrue();
            assertThat(System.nanoTime()).as("the native core recorded its port in time").isLessThan(deadline);
            Thread.sleep(100);
        }
    }

    private static void stop(Process running) throws Exception {
        if (running == null) {
            return;
        }
        running.destroy();
        if (!running.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
            running.destroyForcibly();
        }
    }

    private static void stopTheWorkerServer() throws Exception {
        if (answers("tmux", "-V")) {
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

    private static boolean answers(String... versionCommand) {
        try {
            return run(new ProcessBuilder(versionCommand).redirectErrorStream(true), "").exitCode() == 0;
        } catch (Exception missing) {
            return false;
        }
    }

    private static Path binary(String property) {
        Path binary = Path.of(System.getProperty(property));
        assertThat(binary).as("native executable named by %s", property).isExecutable();
        return binary;
    }

    private static CommandRun edi(Map<String, String> environment, String input, String... arguments) throws Exception {
        List<String> commandLine = new ArrayList<>(List.of(binary("edi.cli.binary").toString()));
        commandLine.addAll(List.of(arguments));
        ProcessBuilder launch = new ProcessBuilder(commandLine).directory(home.toFile()).redirectErrorStream(true);
        Map<String, String> variables = new HashMap<>(environment);
        variables.put("EDI_HOME", home.toString());
        launch.environment().keySet().removeIf(variable -> variable.startsWith("TMUX") || variable.startsWith("CLAUDE") || variable.startsWith("EDI_"));
        launch.environment().putAll(variables);
        return run(launch, input);
    }

    private static CommandRun run(ProcessBuilder launch, String input) throws Exception {
        Path output = Files.createTempFile(homes, "command-", ".out");
        Process command = processes.register(launch.redirectOutput(output.toFile()).start());
        try (OutputStream standardInput = command.getOutputStream()) {
            standardInput.write(input.getBytes(StandardCharsets.UTF_8));
        }
        if (!command.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
            command.destroyForcibly().waitFor();
        }
        return new CommandRun(command.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
    }

    private record CommandRun(int exitCode, String output) {
    }
}
