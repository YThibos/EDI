package io.github.ythibos.edi.application.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.rule.RuleReport.FiredRule;
import io.github.ythibos.edi.application.rule.RuleReport.QuietRule;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.application.worker.ToolCall;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.rule.RuleFired;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class RuleFiringsTest {

    private static final TaskId LAMP = new TaskId("fit-lamp");

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;
    private Path worktree;

    @BeforeEach
    void serveAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip(LAMP.value(), "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        worktree = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), LAMP));
    }

    @Test
    void recordsEachToolCallTheGuardRefusesAWorkerOnceWithItsTask() {
        HookAnswer answer = home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", "pkill -f lamp", null, worktree));

        assertThat(answer.verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
        assertThat(firings()).containsExactly(tuple("guard.signal-by-pattern", "refused", "worker:fit-lamp#1", "fit-lamp", null));
        assertThat(home.eventTypesOf(LAMP)).endsWith("ToolCallRefused");
    }

    @Test
    void recordsWhatTheGuardRefusesThePrimaryWithItsSession() {
        UUID session = UUID.randomUUID();

        home.guardToolCall().checkForPrimary(new ToolCall("Bash", "git push --force origin main", null, home.home().root()),
                new CallingSession(session));

        assertThat(firings()).containsExactly(tuple("guard.forced-push", "refused", "primary", null, session.toString()));
    }

    @Test
    void recordsNothingForAToolCallThatNoRuleConcerns() {
        home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", "mvn -q test", null, worktree));

        assertThat(firings()).isEmpty();
    }

    @Test
    void recordsAStopBlockedByAnUnreadMessage() {
        home.workerHooks().promptSubmitted(worker.id(), "the brief", null);
        home.steerWorker().steer(TaskReference.parse(LAMP.value()), "Use the brass lamp.", null, Actor.PRIMARY);

        home.workerHooks().stopping(worker.id());
        home.workerHooks().stopping(worker.id());

        assertThat(firings()).containsExactly(tuple("hook.stop-with-unread-inbox", "refused", "worker:fit-lamp#1", "fit-lamp", null));
    }

    @Test
    void reportsHowOftenEachRuleFiredAndEveryRuleThatNeverDid() {
        home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", "pkill -f lamp", null, worktree));
        home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", "killall java", null, worktree));
        home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", "git push --force origin feature/HRN-3", null, worktree));

        RuleReport report = home.ruleReport().since(TestHome.START);

        assertThat(report.fired()).extracting(FiredRule::rule, FiredRule::fired, FiredRule::refused, FiredRule::lastFired)
                .containsExactly(
                        tuple("guard.signal-by-pattern", 2L, 2L, TestHome.START),
                        tuple("guard.forced-push", 1L, 1L, TestHome.START));
        assertThat(report.neverFired()).extracting(QuietRule::rule).hasSize(Rule.values().length - 2)
                .doesNotContain("guard.signal-by-pattern", "guard.forced-push")
                .contains("guard.merge", "commit.work-key");
        assertThat(home.ruleReport().since(TestHome.START.plusSeconds(1)).fired()).isEmpty();
    }

    @Test
    void takesInTheFiringsSpooledWhileTheCoreWasOutOfReachAndLeavesOutWhatItCannotRead() throws IOException {
        UUID session = UUID.randomUUID();
        Path spool = home.home().spooledRuleFirings();
        Files.createDirectories(spool.getParent());
        Files.writeString(spool, """
                {"rule":"guard.core-unreachable","outcome":"refused","reason":"The guard could not check this tool call","at":"2026-11-09T08:00:00Z","actor":"primary","session":"%s"}
                {"rule":"guard.no-such-rule","outcome":"refused","reason":"made up","at":"2026-11-09T08:00:01Z","actor":"primary"}
                not json at all
                {"rule":"commit.work-key","outcome":"refused","reason":"the subject starts with the work key HRN-3.","at":"2026-11-09T08:00:02Z","actor":"worker:fit-lamp#1","task":"fit-lamp"}
                """.formatted(session));

        home.takeInSpooledFirings().takeIn();

        assertThat(firings()).containsExactly(
                tuple("guard.core-unreachable", "refused", "primary", null, session.toString()),
                tuple("commit.work-key", "refused", "worker:fit-lamp#1", "fit-lamp", null));
        assertThat(spool).doesNotExist();
        assertThat(spool.resolveSibling(spool.getFileName() + ".taking-in")).doesNotExist();
    }

    @Test
    void takesInThePushTheCommandRefusedWhileThePublishCheckCouldNotReachTheCore() throws IOException {
        Path spool = home.home().spooledRuleFirings();
        Files.createDirectories(spool.getParent());
        Files.writeString(spool, """
                {"rule":"publish.check-reachable","outcome":"refused","reason":"The publish check could not run, so nothing it checks goes out: EDI core is not running.","at":"2026-11-09T08:00:00Z","actor":"worker:fit-lamp#1","task":"fit-lamp"}
                """);

        home.takeInSpooledFirings().takeIn();

        assertThat(firings()).containsExactly(tuple("publish.check-reachable", "refused", "worker:fit-lamp#1", "fit-lamp", null));
    }

    @Test
    void takesInTheBatchAnEarlierRoundLeftBeforeTheNewSpoolAndLeavesOutALineThatIsNotUtf8() throws IOException {
        Path spool = home.home().spooledRuleFirings();
        Files.createDirectories(spool.getParent());
        Files.writeString(spool.resolveSibling(spool.getFileName() + ".taking-in"), spooledLine("guard.core-unreachable"));
        Files.write(spool, bytesOf(spooledLine("commit.work-key"), "{\"rule\":\"hook.not-taken\",\"reason\":\"\u00c3(\"}\n",
                spooledLine("hook.not-taken")));

        home.takeInSpooledFirings().takeIn();

        assertThat(firings()).extracting(firing -> firing.toList().getFirst())
                .containsExactly("guard.core-unreachable", "commit.work-key", "hook.not-taken");
        assertThat(spool).doesNotExist();
    }

    @Test
    void forgetsTheFiringsKeptLongerThanFourHundredDays() {
        FiredRules fired = home.firedRules();
        fired.record(RuleFired.refused(Rule.MERGE, Actor.PRIMARY, null, "only the captain merges."));
        home.clock().advance(Duration.ofDays(200));
        fired.record(RuleFired.refused(Rule.FORCED_PUSH, Actor.PRIMARY, null, "a forced push rewrites history on the remote (I-03)."));
        home.clock().advance(Duration.ofDays(201));

        fired.forgetTheFiringsPastTheirKeeping();

        assertThat(firings()).extracting(firing -> firing.toList().getFirst()).containsExactly("guard.forced-push");
    }

    private static String spooledLine(String rule) {
        return "{\"rule\":\"" + rule + "\",\"outcome\":\"refused\",\"reason\":\"fired while the core was down\",\"at\":\"2026-11-09T08:00:00Z\","
                + "\"actor\":\"primary\"}\n";
    }

    private static byte[] bytesOf(String before, String notUtf8, String after) {
        byte[] invalid = notUtf8.getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer bytes = ByteBuffer.allocate(before.length() + invalid.length + after.length());
        return bytes.put(before.getBytes(StandardCharsets.UTF_8)).put(invalid).put(after.getBytes(StandardCharsets.UTF_8)).array();
    }

    private List<Tuple> firings() {
        return home.jdbc().sql("select rule, outcome, actor, task_id, session_id from rule_fired order by number")
                .query((row, _) -> tuple(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5)))
                .list();
    }
}
