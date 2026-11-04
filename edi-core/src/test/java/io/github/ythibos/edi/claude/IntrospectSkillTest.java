package io.github.ythibos.edi.claude;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.status.StatusKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.Patience;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class IntrospectSkillTest {

    private static final Path SCRIPT = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize()
            .resolve("claude/skills/introspect/introspect.mjs");
    private static final String SESSION = "5e55a0e1-0000-4000-8000-000000000001";

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker lamp;

    @BeforeEach
    void inventAStore() throws IOException {
        assumeTrue(InstalledPrograms.answers("node", "-e", "require('node:sqlite')"), "node with node:sqlite is not installed");
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        lamp = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    @Test
    void printsOneTimelineOfStatusesWakesAndSteers() throws Exception {
        home.recordStatus().report(lamp.id(), StatusState.NEEDS_DECISION, new StatusKey("socket"), null, "Which socket does the lamp take?");
        printAndAcknowledgeTheWake(1, Duration.ofMinutes(1));
        home.clock().advance(Duration.ofMinutes(3));
        home.steerWorker().steer(new TaskReference.ById(lamp.id().task()), "The bayonet socket.", new StatusKey("socket"), Actor.PRIMARY);

        String report = introspect();

        assertThat(report).contains(
                "Flags (0):\n  none\n",
                "  2026-10-15 09:00  fit-lamp  fit-lamp#1 reports needs-decision (socket): Which socket does the lamp take?\n",
                "  2026-10-15 09:00  fit-lamp  wake 1 needs-decision raised: (socket) Which socket does the lamp take?\n",
                "  2026-10-15 09:00  fit-lamp  wake 1 printed to session 5e55a0e1\n",
                "  2026-10-15 09:01  fit-lamp  wake 1 acknowledged\n",
                "  2026-10-15 09:03  fit-lamp  steer 1 sent by primary, resolving socket\n");
    }

    @Test
    void flagsAWakeLetPassAQuestionLeftOpenAGateParkedTwoWaitsAndAReportWhileAWorkerWaited() throws Exception {
        Worker horn = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("tune-horn", "harness", "HRN-4"), CategoryId.IMPLEMENTATION);
        home.recordStatus().report(lamp.id(), StatusState.NEEDS_DECISION, new StatusKey("socket"), null, "Which socket?");
        home.recordStatus().report(horn.id(), StatusState.NEEDS_DECISION, new StatusKey("nm-01RUN-review"), null, "Fix the finding or skip it?");
        printAndAcknowledgeTheWake(1, Duration.ofMinutes(1));
        startAWait("w1", true, TestHome.START.minus(Duration.ofMinutes(30)), null);
        startAWait("w2", false, TestHome.START.minus(Duration.ofMinutes(5)), TestHome.START.plus(Duration.ofMinutes(1)));
        reportATurn(TestHome.START.plus(Duration.ofMinutes(20)));
        String until = TestHome.START.plus(Duration.ofMinutes(25)).toString();

        String report = introspect("--until", until);

        assertThat(report).contains(
                "  2026-10-15 08:55  overlapping-waits  two edi waits ran at once in session 5e55a0e1, so either could take the other's wakes\n",
                "  2026-10-15 09:00  question-left-open  the question socket of fit-lamp#1 waited 25 minutes and still has no resolving steer,"
                        + " past the 10-minute threshold\n",
                "  2026-10-15 09:01  acknowledged-without-action  wake 1 (needs-decision) was acknowledged, and the primary did nothing on fit-lamp"
                        + " after it was printed\n",
                "  2026-10-15 09:20  report-while-a-worker-waited  a turn report was rendered in session 5e55a0e1 while the question socket of"
                        + " fit-lamp#1 waited on the primary\n");
        assertThat(report).doesNotContain("gate-parked");
        assertThat(introspect("--until", until, "--gate-minutes", "20")).contains("  2026-10-15 09:00  gate-parked  the gate nm-01RUN-review of tune-horn#1 waited 25 minutes");
    }

    @Test
    void narrowsTheTimelineToOneTaskOrTheTaskOfAWake() throws Exception {
        Worker horn = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("tune-horn", "harness", "HRN-4"), CategoryId.IMPLEMENTATION);
        home.recordStatus().report(lamp.id(), StatusState.BLOCKED, new StatusKey("socket"), null, "The socket is missing.");
        home.recordStatus().report(horn.id(), StatusState.WORKING, null, null, "Tuning the horn.");

        assertThat(introspect("--task", "tune-horn")).contains("tune-horn#1 reports working: Tuning the horn.").doesNotContain("socket");
        assertThat(introspect("--wake", "1")).contains("Introspect: 2026-10-15T08:00:00Z to 2026-10-15T10:00:00Z, task fit-lamp\n")
                .doesNotContain("Tuning the horn");
    }

    @Test
    void neverPrintsASecretAndKeepsItsOutputShortUnlessAskedForTheFullDetail() throws Exception {
        String token = "ghp_" + "a".repeat(36);
        home.recordStatus().report(lamp.id(), StatusState.WORKING, null, null, "Pushed with " + token + " for the lamp.");
        Stream.of("one", "two", "three").forEach(step -> home.recordStatus().report(lamp.id(), StatusState.WORKING, null, null, "Step " + step + "."));

        String report = introspect("--max-lines", "2");

        assertThat(report).doesNotContain(token, "Step one.").contains(" earlier lines left out; --full shows them\n", "Step two.", "Step three.");
        assertThat(introspect("--full")).doesNotContain(token).contains("Pushed with [redacted] for the lamp.");
    }

    @Test
    void writesOneBacklogDraftPerFlagWithItsFieldsAndContextFilled() throws Exception {
        home.recordStatus().report(lamp.id(), StatusState.NEEDS_DECISION, new StatusKey("socket"), null, "Which socket?");
        Path drafts = directory.resolve("drafts");

        String report = introspect("--until", TestHome.START.plus(Duration.ofMinutes(30)).toString(), "--drafts", drafts.toString());

        Path draft = drafts.resolve("introspect-01-question-left-open.md");
        assertThat(report).contains("Drafts (1):\n  " + draft + "\n");
        assertThat(Files.readString(draft)).startsWith("""
                ---
                title: An unanswered worker question comes back until it is answered
                kind: ship
                branch: feature
                source: Introspection of 2026-10-15, after question-left-open on fit-lamp
                size: S
                section: Wave 1
                blocked-by: -
                status: open
                ---
                ## Goal
                """).contains("""
                ## Context

                The question socket of fit-lamp#1 waited 30 minutes and still has no resolving steer, past the 10-minute threshold.
                """, "- 2026-10-15 09:00 fit-lamp: fit-lamp#1 reports needs-decision (socket): Which socket?\n",
                "## Acceptance criteria", "## Requirements from decisions and answers", "## Read first", "## How to verify");
    }

    private void printAndAcknowledgeTheWake(long number, Duration after) {
        Instant printed = home.clock().now();
        home.jdbc().sql("update wake set delivered_to = :session, delivered_at = :printed, acknowledged_at = :acknowledged where number = :number")
                .param("session", SESSION)
                .param("printed", printed.toString())
                .param("acknowledged", printed.plus(after).toString())
                .param("number", number)
                .update();
    }

    private void startAWait(String id, boolean follows, Instant started, Instant ended) {
        home.jdbc().sql("insert into wake_stream (id, session_id, pid, follows, started_at, ended_at) values (:id, :session, 4242, :follows, :started, :ended)")
                .param("id", id)
                .param("session", SESSION)
                .param("follows", follows ? 1 : 0)
                .param("started", started.toString())
                .param("ended", ended == null ? null : ended.toString())
                .update();
    }

    private void reportATurn(Instant at) {
        home.jdbc().sql("insert into primary_session (session_id, started_at, reported_at) values (:session, :started, :reported)")
                .param("session", SESSION)
                .param("started", TestHome.START.minus(Duration.ofHours(1)).toString())
                .param("reported", at.toString())
                .update();
    }

    private String introspect(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("node", SCRIPT.toString(), "--store", home.home().store().toString(),
                "--since", TestHome.START.minus(Duration.ofHours(1)).toString(), "--until", TestHome.START.plus(Duration.ofHours(1)).toString()));
        command.addAll(List.of(arguments));
        Process process = processes.register(new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start());
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)).as("the script finished").isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
