package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.Citation;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

class LoginCredentialsTest {

    private static final String CREDENTIALS = ".credentials.json";
    private static final String A_SECRET = "touches a secret, because it names a file that holds a secret";

    @TempDir
    private Path directory;

    @TempDir
    private Path machine;

    private TestHome home;
    private Worker worker;
    private Path worktree;
    private Path defaultLogin;
    private Path secondLogin;

    @BeforeEach
    void serveAWorkerOnAMachineWithTwoClaudeLogins() throws IOException {
        defaultLogin = loginDirectory("claude-home/.claude");
        secondLogin = loginDirectory("logins/second");
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n").withTheClaudeLogin("second", secondLogin);
        home.runtime().keepsClaudeCodesOwnDirectoryIn(defaultLogin);
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        worktree = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp")));
    }

    @Test
    void refusesAWorkerAReadOfACredentialsFileWhicheverDirectoryHoldsIt() throws IOException {
        Path unnamedLogin = loginDirectory("logins/unnamed");
        Path linked = Files.createSymbolicLink(worktree.resolve("notes.json"), secondLogin.resolve(CREDENTIALS));

        for (Path credentials : List.of(defaultLogin.resolve(CREDENTIALS), secondLogin.resolve(CREDENTIALS), unnamedLogin.resolve(CREDENTIALS), linked)) {
            assertThat(workerRead(credentials).reason()).as(credentials.toString()).contains("this read " + A_SECRET);
        }
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).filteredOn("ToolCallRefused"::equals).hasSize(4);
    }

    @Test
    void refusesAWorkerAShellCommandThatNamesACredentialsFile() throws IOException {
        Files.createSymbolicLink(worktree.resolve("notes.json"), defaultLogin.resolve(CREDENTIALS));
        List<String> commands = List.of(
                "cat " + defaultLogin.resolve(CREDENTIALS),
                "base64 < " + secondLogin.resolve(CREDENTIALS),
                "cat " + secondLogin + "/.cred*",
                "jq . " + machine + "/logins/*/" + CREDENTIALS,
                "cat notes.json");

        for (String command : commands) {
            assertThat(workerShell(command).reason()).as(command).contains("this command " + A_SECRET);
        }
        assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).filteredOn("ToolCallRefused"::equals).hasSize(commands.size());
    }

    @Test
    void refusesAWorkerAFileSearchThatReachesTheCredentialsOfAnyLoginOfTheProfile() {
        for (ToolCall search : List.of(new ToolCall("Glob", null, defaultLogin.toString(), worktree, ".cred*"),
                new ToolCall("Glob", null, secondLogin.toString(), worktree, "{.credentials,settings}.json"),
                new ToolCall("Glob", null, machine.toString(), worktree, "**/" + CREDENTIALS),
                new ToolCall("Glob", null, null, worktree, defaultLogin + "/.credentials.js?n"))) {
            assertThat(home.guardToolCall().checkForWorker(worker.id(), search).reason()).as(search.toString()).contains("this file search " + A_SECRET);
        }
    }

    @Test
    void refusesThePrimaryEveryLoginsCredentialsUntilTheCaptainsWordInThisSession() {
        CallingSession session = primarySessionIn("Check that the second login still works.");
        List<ToolCall> calls = List.of(
                new ToolCall("Read", null, defaultLogin.resolve(CREDENTIALS).toString(), home.home().root()),
                new ToolCall("Read", null, secondLogin.resolve(CREDENTIALS).toString(), home.home().root()),
                new ToolCall("Bash", "cat " + defaultLogin.resolve(CREDENTIALS), null, home.home().root()),
                new ToolCall("Bash", "head -c 32 " + secondLogin.resolve(CREDENTIALS), null, home.home().root()));

        for (ToolCall call : calls) {
            assertThat(home.guardToolCall().checkForPrimary(call, session).reason()).as(call.toString()).contains(A_SECRET, "edi captain said");
        }

        home.captainsWord().record(session, new Citation.WholeMessage(1), null, null, Actor.PRIMARY);

        for (ToolCall call : calls) {
            assertThat(home.guardToolCall().checkForPrimary(call, session)).as(call.toString()).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Test
    void letsAWorkerReadSearchAndRunItsUsualCommandsInItsCopy() throws IOException {
        Files.writeString(worktree.resolve("Lamp.java"), "class Lamp {}\n");
        Files.writeString(worktree.resolve("credentials.json"), "{}\n");

        assertThat(workerRead(worktree.resolve("Lamp.java"))).isEqualTo(HookAnswer.PROCEED);
        assertThat(workerRead(worktree.resolve("credentials.json"))).isEqualTo(HookAnswer.PROCEED);
        for (String command : List.of("cat Lamp.java", "cat credentials.json", "ls " + secondLogin, "git status", "./mvnw test -Pfast")) {
            assertThat(workerShell(command)).as(command).isEqualTo(HookAnswer.PROCEED);
        }
        assertThat(home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Glob", null, null, worktree, "**/*.java"))).isEqualTo(HookAnswer.PROCEED);
    }

    private Path loginDirectory(String path) throws IOException {
        Path login = Files.createDirectories(machine.resolve(path));
        Files.writeString(login.resolve(CREDENTIALS), "{\"stand-in\":\"for the test only\"}\n");
        return login;
    }

    private HookAnswer workerRead(Path file) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Read", null, file.toString(), worktree));
    }

    private HookAnswer workerShell(String command) {
        return home.guardToolCall().checkForWorker(worker.id(), new ToolCall("Bash", command, null, worktree));
    }

    private CallingSession primarySessionIn(String typed) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(session, typed);
        return new CallingSession(session);
    }
}
