package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.tracker.JiraCalls;
import io.github.ythibos.edi.adapter.tracker.JiraTracker;
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.application.worker.ToolCall;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.TicketDetails;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.CapturedLogs;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.StubJiraServer;
import io.github.ythibos.edi.testing.SyntheticSecrets;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TrackerFixtures;
import io.github.ythibos.edi.testing.WorkEvents;
import io.github.ythibos.edi.testing.WorkerFixtures;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("4.6 Process safety")
class ProcessSafetyTest {

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;

    private void prepareAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
    }

    private HookAnswer forTheWorker(String command) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", command, null, home.home().worktreeOf(
                new ProjectName("harness"), new TaskId("fit-lamp"))));
    }

    private HookAnswer forThePrimary(String command) {
        return home.guardToolCall().checkForPrimary(new ToolCall("Bash", command, null, home.home().root()), CallingSession.UNNAMED);
    }

    private void assertDenied(HookAnswer answer, String reason) {
        assertThat(answer.verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
        assertThat(answer.reason()).startsWith("The guard refuses this: ").contains(reason);
    }

    @Nested
    @DisplayName("I-30 No broad process signals")
    class NoBroadProcessSignals {

        @Test
        @DisplayName("AT-30 the pre-tool guard refuses pkill, killall and kill $(pgrep ...) in worker and primary Bash calls")
        void theGuardRefusesPatternKills() throws IOException {
            prepareAWorker();

            for (String command : List.of("pkill -f surefire", "killall java", "kill $(pgrep -f surefire)", "kill -9 `pgrep java`",
                    "pgrep -f mvn | xargs kill", "sudo pkill node", "bash -c 'pkill -f vite'")) {
                assertDenied(forTheWorker(command), "kill only a process id you started and captured (I-30)");
                assertDenied(forThePrimary(command), "kill only a process id you started and captured (I-30)");
            }
            assertThat(forTheWorker("kill \"$build_pid\"").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
            assertThat(forThePrimary("pgrep -f surefire").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).filteredOn("ToolCallRefused"::equals).hasSize(7);
        }
    }

    @Nested
    @DisplayName("I-31 Workers live on EDI's private tmux socket, and nobody runs bare-socket tmux")
    class WorkersLiveOnThePrivateSocket {

        @Test
        @DisplayName("AT-31 the guard refuses tmux kill-server, tmux kill-session without a socket, and tmux -L edi kill-server from a worker")
        void theGuardRefusesBareSocketTmux() throws IOException {
            prepareAWorker();

            for (String command : List.of("tmux kill-server", "tmux kill-session -t workers", "tmux ls")) {
                assertDenied(forTheWorker(command), "name the socket with -L or -S (I-31)");
                assertDenied(forThePrimary(command), "name the socket with -L or -S (I-31)");
            }
            for (String command : List.of("tmux -L edi kill-server", "tmux -L edi kill-window -t @2", "tmux -L edi send-keys -t @2 Enter")) {
                assertDenied(forTheWorker(command), "belong to the core");
                assertDenied(forThePrimary(command), "belong to the core");
            }
            assertThat(forTheWorker("tmux -L edi-test-42 kill-server").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
            assertThat(forTheWorker("tmux -L edi list-windows").verdict()).isEqualTo(HookAnswer.Verdict.PROCEED);
        }
    }

    @Nested
    @DisplayName("I-32 Secrets are read at call time and never printed")
    class SecretsAreNeverPrinted {

        @Test
        @DisplayName("AT-32 a canary secret in a fixture keys file never appears in logs, events or stdout after a full adapter run")
        @Tag("adapter")
        void aCanarySecretNeverSurfaces() throws IOException {
            String canary = SyntheticSecrets.canary();
            List<String> said = new ArrayList<>();
            try (StubJiraServer jira = StubJiraServer.acceptingToken(canary).holding("SVC-40", "Harbour lamps", "To Do");
                    JiraCalls calls = new JiraCalls(new JsonMapper());
                    CapturedLogs logs = CapturedLogs.ofEveryLogger()) {
                TestHome home = homeReachingThe(jira, canary);
                home.trackingWith(new JiraTracker(home.machineProfile(), calls, new JsonMapper()));

                runEveryTrackerCall(home, jira, canary, said);

                assertThat(jira.exchanges()).allSatisfy(exchange -> assertThat(exchange.authorization()).isIn("Bearer " + canary, null));
                assertThat(jira.exchanges()).hasSizeGreaterThan(10);
                assertThat(logs.lines()).noneMatch(line -> line.contains(canary));
                assertThat(said).noneMatch(line -> line.contains(canary)).anyMatch(line -> line.contains("[secret]"));
                assertThat(home.jdbc().sql("select payload from event").query(String.class).list()).noneMatch(payload -> payload.contains(canary))
                        .anyMatch(payload -> payload.contains("[secret]"));
                for (Path written : List.of(home.home().store(), home.home().store().resolveSibling("edi.db-wal"), home.home().fleetLedger())) {
                    assertThat(Files.exists(written) ? Files.readString(written, StandardCharsets.ISO_8859_1) : "").as(written.toString()).doesNotContain(canary);
                }
            }
        }

        private TestHome homeReachingThe(StubJiraServer jira, String canary) throws IOException {
            TestHome home = TestHome.in(directory).registering(TrackerFixtures.REGISTRY).writing("keys/tracker-keys.yml", "TRACKER:\n  API-key: " + canary + "\n");
            return home.writing("config/machine.yaml", MachineFixture.LAPTOP.profile()
                    .replace("https://tracker.example.org", jira.address().toString())
                    .replace("/nonexistent/tracker-keys.yml", home.home().root().resolve("keys/tracker-keys.yml").toString()));
        }

        private void runEveryTrackerCall(TestHome home, StubJiraServer jira, String canary, List<String> said) {
            TicketDetails details = new TicketDetails(null, WorkKey.parse("SVC-9"), List.of("lighting"), "Major");
            said.add(home.addTask().add(TrackerFixtures.ship("fix-lamps", "service", "Fix the harbour lamps", null, details), Actor.PRIMARY).toString());
            said.add(home.addTask().add(TrackerFixtures.ship("fit-horn", "service", "Fit the horn", "SVC-40"), Actor.PRIMARY).toString());
            said.add(home.answerDecision().answer(TaskReference.parse("fix-lamps"), new GivenAnswer("D58 B", OptionKey.parse("B"), "", AnswerChannel.CHAT),
                    Actor.PRIMARY).ticket().toString());
            WorkEvents.of(home, "fix-lamps").dispatchedTo(1, "feature/SVC-101").deliveredAsADraft().merged();
            WorkEvents.of(home, "fit-horn").dispatchedTo(1, "feature/SVC-40");
            jira.answersNextWith(400, "{\"errorMessages\":[\"Rejected the call that carried " + canary + "\"]}");
            home.eventFollowers().followTheLog();
            jira.answersNextWith(401, "");
            said.add(catchThrowable(() -> home.addTask().add(TrackerFixtures.ship("fit-bell", "service", "Fit the bell", "SVC-40"), Actor.PRIMARY))
                    .getMessage());
            jira.answersNextWith(500, "{\"errorMessages\":[\"Failed on " + canary + "\"]}");
            said.add(catchThrowable(() -> home.addTask().add(TrackerFixtures.ship("fit-buoy", "service", "Fit the buoy", "SVC-40"), Actor.PRIMARY))
                    .getMessage());
            said.addAll(home.jdbc().sql("select coalesce(detail, '') from wake").query(String.class).list());
        }
    }
}
