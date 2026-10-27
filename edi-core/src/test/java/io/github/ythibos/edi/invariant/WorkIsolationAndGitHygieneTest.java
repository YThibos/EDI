package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.application.worker.ToolCall;
import io.github.ythibos.edi.application.worker.WorkerContext.Workplace;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.branch.BranchKind;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.landing.BranchCommit;
import io.github.ythibos.edi.domain.landing.LandedWork;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.ExampleHome;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("4.2 Work isolation and git hygiene")
class WorkIsolationAndGitHygieneTest {

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-07 Workers never touch the primary copy or the captain's checkouts")
    class WorkersStayInTheirWorktree {

        @Test
        @DisplayName("AT-07a a worker launch refuses a worktree outside $EDI_HOME/worktrees/ or in the project clone's primary directory")
        @Tag("adapter")
        void launchRefusesAWorktreeOutsideTheHome() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("fit-horn", "EDI", "EDI-9")));
            Files.createDirectories(home.home().worktrees());
            Files.createSymbolicLink(home.home().worktrees().resolve("harness"), Files.createDirectories(directory.resolve("elsewhere")));
            Path clonePlacedInTheWorktrees = home.home().worktrees().resolve("EDI/fit-horn");
            FixtureRepository.at(home.home().root().resolve("projects/EDI")).clonedInto(clonePlacedInTheWorktrees);
            home.registering("""
                    projects:
                      - name: harness
                        tracker: { kind: edi, projectKey: HRN }
                        base: main
                      - name: EDI
                        clone: %s
                        tracker: { kind: edi, projectKey: EDI }
                        base: main
                    """.formatted(clonePlacedInTheWorktrees));

            assertThatThrownBy(() -> dispatch(home, "fit-lamp")).isInstanceOf(Refusal.class).hasMessageContaining("is outside " + home.home().worktrees());
            assertThatThrownBy(() -> dispatch(home, "fit-horn")).isInstanceOf(Refusal.class).hasMessageContaining("is the primary directory of a clone");
            assertThat(directory.resolve("elsewhere")).isEmptyDirectory();
            assertThat(home.runtime().launches()).isEmpty();
        }

        @Test
        @DisplayName("AT-07b delivery into a captain checkout is a ref-only fetch that leaves a dirty checkout byte-identical")
        @Tag("adapter")
        void deliveryLeavesTheCaptainsCheckoutUntouched() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            FixtureRepository checkout = FixtureRepository.at(home.home().root().resolve("projects/harness")).clonedInto(directory.resolve("checkouts/harness"));
            checkout.write("README.md", "half-edited by the captain\n").write("notes.txt", "untracked\n");
            home.registering("""
                    projects:
                      - name: harness
                        tracker: { kind: edi, projectKey: HRN }
                        base: main
                        delivery: local-branch
                        captainCheckout: %s
                    """.formatted(checkout.root()));
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            DispatchOutcome dispatched = dispatch(home, "fit-lamp");
            FixtureRepository.at(dispatched.launched().worktree()).write("lamp.txt", "brass\n").commit("HRN-3 Fit the lamp");
            Map<Path, String> filesBefore = filesOf(checkout.root());
            String headBefore = checkout.head();

            home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "The lamp is fitted.");

            assertThat(checkout.run("rev-parse", "refs/heads/feature/HRN-3").strip()).isEqualTo(FixtureRepository.at(dispatched.launched().worktree()).head());
            assertThat(filesOf(checkout.root())).isEqualTo(filesBefore);
            assertThat(checkout.head()).isEqualTo(headBefore);
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("BranchDeliveredToCaptain");

            String captainsOwnCommit = checkout.run("commit-tree", "HEAD^{tree}", "-p", "HEAD", "-m", "The captain's own commit").strip();
            checkout.run("update-ref", "refs/heads/feature/HRN-3", captainsOwnCommit);
            Map<Path, String> filesBeforeRefusal = filesOf(checkout.root());

            home.recordStatus().report(dispatched.launched().worker().id(), StatusState.DONE, null, null, "The lamp is fitted again.");

            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("BranchDeliveryFailed");
            assertThat(filesOf(checkout.root())).isEqualTo(filesBeforeRefusal);
            assertThat(checkout.run("rev-parse", "refs/heads/feature/HRN-3").strip()).isEqualTo(captainsOwnCommit);
        }
    }

    @Nested
    @DisplayName("I-08 Branch names carry the work key, never fm/ or edi/")
    class BranchNamesCarryTheWorkKey {

        @Test
        @DisplayName("AT-08 branch naming refuses a missing key and any fm/ or edi/ prefix for every project, including EDI itself")
        void branchNamingRefusesAMissingKeyAndForbiddenPrefixes() throws IOException {
            TestHome home = TestHome.in(directory).registering("""
                    projects:
                      - name: harness
                        tracker: { kind: edi, projectKey: HRN }
                        base: main
                      - name: EDI
                        tracker: { kind: edi, projectKey: EDI }
                        base: main
                    """).writing("config/dispatch-rules.json", ExampleHome.file("config/dispatch-rules.json"));

            for (ProjectEntry project : home.registry().entries()) {
                String key = project.project().workKeys().value();
                home.storing(List.of(WorkerFixtures.queuedShip("unkeyed-" + key.toLowerCase(Locale.ROOT), project.project().name().value(), null)));
                assertThatThrownBy(() -> dispatch(home, "unkeyed-" + key.toLowerCase(Locale.ROOT)))
                        .isInstanceOf(Refusal.class).hasMessageContaining("has no work key");
                for (BranchKind kind : BranchKind.values()) {
                    assertThat(BranchName.forWork(kind, WorkKey.parse(key + "-7")))
                            .hasToString(kind.word() + "/" + key + "-7");
                }
                for (String harnessBranch : List.of("fm/" + key + "-7", "edi/" + key + "-7", "fm/feature/" + key + "-7")) {
                    assertThatThrownBy(() -> BranchName.parse(harnessBranch)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("never starts with");
                }
                assertThatThrownBy(() -> BranchName.parse("feature/no-key")).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BranchName.forWork(BranchKind.FEATURE, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("carries its work key");
            }
            assertThat(home.runtime().launches()).isEmpty();
        }
    }

    @Nested
    @DisplayName("I-09 No co-author or agent trailer, ever")
    class NoAgentTrailer {

        @Test
        @DisplayName("AT-09 the commit-msg hook rejects each forbidden trailer form, the guard refuses a commit that would skip it,"
                + " and launch settings carry the attribution block")
        @Tag("adapter")
        void theCommitHookRejectsAgentTrailers() throws IOException, InterruptedException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            FixtureRepository.at(home.home().root().resolve("projects/harness"))
                    .configure("credential.https://forge.example.org.helper", "!f() { echo username=worker; echo password=scoped-secret; }; f");
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            DispatchOutcome.Launched launched = dispatch(home, "fit-lamp").launched();
            FixtureRepository worktree = FixtureRepository.at(launched.worktree());

            for (String trailer : List.of(
                    "Co-authored-by: Claude <noreply@anthropic.com>",
                    "co-authored-by: A Person <person@example.org>",
                    "Signed-off-by: Claude Opus <noreply@anthropic.com>",
                    "Signed-off-by: Release Bot <bot@example.org>",
                    "\uD83E\uDD16 Generated with [Claude Code](https://claude.com/claude-code)",
                    "Generated with an AI assistant")) {
                worktree.write("lamp.txt", trailer);
                assertThatThrownBy(() -> worktree.commit("HRN-3 Fit the lamp\n\nThe brass one.\n\n" + trailer))
                        .isInstanceOf(IOException.class).hasMessageContaining("EDI commit-msg hook");
            }
            worktree.write("lamp.txt", "accepted");
            worktree.commit("HRN-3 Fit the lamp\n\nFiles generated with protoc stay as they are.\n\nSigned-off-by: Fixture Author <fixture@example.org>");
            assertRefusedBeforeGitRuns(guarded(home, launched, "git commit --no-verify -m 'HRN-3 Fit the lamp\n\nCo-authored-by: Claude <noreply@anthropic.com>'"),
                    "trailer out (I-09)");

            JsonNode settings = new JsonMapper().readTree(resource("claude/worker/settings.json"));
            assertThat(settings.path("attribution").path("commit").asString()).isEmpty();
            assertThat(settings.path("attribution").path("pr").asString()).isEmpty();
            assertThat(settings.path("attribution").path("sessionUrl").asBoolean(true)).isFalse();
            assertThat(credentialAnsweredIn(worktree.root())).contains("password=scoped-secret");
        }
    }

    @Nested
    @DisplayName("I-10 Every commit carries its key")
    class EveryCommitCarriesItsKey {

        @Test
        @DisplayName("AT-10 the commit-msg hook rejects a subject without the task's key, and the guard refuses a commit that would skip it")
        @Tag("adapter")
        void theCommitHookRejectsASubjectWithoutTheKey() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            DispatchOutcome.Launched launched = dispatch(home, "fit-lamp").launched();
            FixtureRepository worktree = FixtureRepository.at(launched.worktree());

            for (String subject : List.of("Fit the lamp", "HRN-4 Fit the lamp", "HRN-30 Fit the lamp", "fit-lamp HRN-3", "# HRN-3 commented out")) {
                worktree.write("lamp.txt", subject);
                assertThatThrownBy(() -> worktree.commit(subject)).isInstanceOf(IOException.class).hasMessageContaining("starts with the work key HRN-3");
            }
            for (String subject : List.of("HRN-3 Fit the lamp", "HRN-3: Fit the horn")) {
                worktree.write("lamp.txt", subject);
                worktree.commit(subject);
            }
            assertThat(worktree.run("log", "--format=%s", "-2")).isEqualTo("HRN-3: Fit the horn\nHRN-3 Fit the lamp\n");
            assertRefusedBeforeGitRuns(guarded(home, launched, "git commit -n -m 'Fit the lamp'"), "head of the subject (I-10)");
        }
    }

    @Nested
    @DisplayName("I-11 No SNAPSHOT in push-bound work")
    class NoSnapshotInPushBoundWork {

        @Test
        @DisplayName("AT-11 a branch adding a SNAPSHOT dependency makes edi status done refuse with the file and line")
        @Tag("adapter")
        void doneRefusesASnapshotDependency() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            FixtureRepository.at(directory.resolve("upstream/harness")).write("pom.xml", BUILD_FILE.formatted("")).commit("Add the build file");
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            DispatchOutcome dispatched = dispatch(home, "fit-lamp");
            Worker worker = dispatched.launched().worker();
            FixtureRepository worktree = FixtureRepository.at(dispatched.launched().worktree())
                    .write("pom.xml", BUILD_FILE.formatted(LAMP_DEPENDENCY.formatted("1.2.0-SNAPSHOT")))
                    .write("module/pom.xml", "<project>\n  <version>2.0.0-SNAPSHOT</version>\n</project>\n")
                    .commit("HRN-3 Use the snapshot lamp");
            WorkerFixtures.opensTheDraftOf(home, worker);

            assertThatThrownBy(() -> home.recordStatus().report(worker.id(), StatusState.DONE, null, null, "The lamp is fitted."))
                    .isInstanceOf(Refusal.class)
                    .hasMessageEndingWith("push-bound work carries no -SNAPSHOT version, and the branch adds one at module/pom.xml:2, pom.xml:6.");
            assertThat(home.workers().get(worker.id()).orElseThrow().lastStatus()).isNull();
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("DeliveryRefused");

            worktree.write("pom.xml", BUILD_FILE.formatted(LAMP_DEPENDENCY.formatted("1.2.0")))
                    .write("module/pom.xml", "<project>\n  <version>2.0.0</version>\n</project>\n")
                    .commit("HRN-3 Use the released lamp");
            WorkerFixtures.opensTheDraftOf(home, worker);
            home.recordStatus().report(worker.id(), StatusState.DONE, null, null, "The lamp is fitted.");

            assertThat(home.workers().get(worker.id()).orElseThrow().doneCheck()).isEqualTo(DoneCheck.ACCEPTED);
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("StatusReported", "DraftRecorded");
        }
    }

    @Nested
    @DisplayName("I-12 Never discard unlanded work")
    class NeverDiscardUnlandedWork {

        @Test
        @DisplayName("AT-12 the landed-work test tells uncommitted, unpushed and unmerged work from squash-merged or rebuilt work")
        @Tag("adapter")
        void theLandedWorkTestRecognisesLandedAndUnlandedWork() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            FixtureRepository upstream = FixtureRepository.at(directory.resolve("upstream/harness"));
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), WorkerFixtures.queuedShip("tune-horn", "harness", "HRN-4")));
            FixtureRepository lamp = FixtureRepository.at(dispatch(home, "fit-lamp").launched().worktree());
            FixtureRepository horn = FixtureRepository.at(dispatch(home, "tune-horn").launched().worktree());
            Workplace lampPlace = home.workerContext().of(new TaskId("fit-lamp"));
            Workplace hornPlace = home.workerContext().of(new TaskId("tune-horn"));

            lamp.write("lamp.txt", "brass\n");
            assertThat(home.landedWork().of(lampPlace).findings()).containsExactly("lamp.txt is not committed");

            lamp.commit("HRN-3 Fit the lamp");
            String unpushed = "commit " + lamp.head().substring(0, 12) + " \"HRN-3 Fit the lamp\" is not in origin/main";
            assertThat(home.landedWork().of(lampPlace).findings()).containsExactly(unpushed);

            upstream.run("fetch", "--quiet", lamp.root().toString(), "feature/HRN-3:feature/HRN-3");
            assertThat(home.landedWork().of(lampPlace).findings()).as("pushed but unmerged").containsExactly(unpushed);

            upstream.run("merge", "--quiet", "--squash", "feature/HRN-3");
            upstream.commit("Fit the lamp, squashed");
            LandedWork squashed = home.landedWork().of(lampPlace);
            assertThat(squashed.isLanded()).isTrue();
            assertThat(squashed.contentInTheBase()).isTrue();

            horn.write("horn.txt", "loud\n").commit("HRN-4 Tune the horn");
            upstream.run("fetch", "--quiet", horn.root().toString(), "feature/HRN-4:feature/HRN-4");
            upstream.run("cherry-pick", "feature/HRN-4");
            upstream.write("horn.txt", "quiet\n").commit("Tone the horn down");
            LandedWork rebuilt = home.landedWork().of(hornPlace);
            assertThat(rebuilt.isLanded()).as("the pipeline rebuilt the commit on a newer base").isTrue();
            assertThat(rebuilt.contentInTheBase()).isFalse();
            assertThat(rebuilt.commitsBeyondTheBase()).singleElement().extracting(BranchCommit::sameChangeInTheBase).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("I-13 Base branches come from the origin ref")
    class BaseBranchesComeFromOrigin {

        @Test
        @DisplayName("AT-13 worktree creation fetches and starts at the origin ref so a stale local ref does not leak in")
        @Tag("adapter")
        void worktreesStartAtTheFreshOriginRef() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            FixtureRepository clone = FixtureRepository.at(home.home().root().resolve("projects/harness"));
            String staleLocalCommit = clone.write("stale.txt", "only here\n").commit("A commit only the clone's local main holds").head();
            String freshOrigin = FixtureRepository.at(directory.resolve("upstream/harness")).write("lamp.txt", "brass\n").commit("Move main on").head();
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));

            FixtureRepository worktree = FixtureRepository.at(dispatch(home, "fit-lamp").launched().worktree());

            assertThat(worktree.head()).isEqualTo(freshOrigin);
            assertThat(clone.run("rev-parse", "refs/remotes/origin/main").strip()).isEqualTo(freshOrigin);
            assertThat(worktree.run("rev-list", "HEAD")).doesNotContain(staleLocalCommit);
            assertThat(worktree.root().resolve("stale.txt")).doesNotExist();
        }
    }

    private static final String BUILD_FILE = """
            <project>
              <version>3.1.0-SNAPSHOT</version>
              <dependencies>
            %s  </dependencies>
            </project>
            """;
    private static final String LAMP_DEPENDENCY = """
                <dependency>
                  <artifactId>lamp</artifactId>
                  <version>%s</version>
                </dependency>
            """;

    private static DispatchOutcome dispatch(TestHome home, String task) {
        return home.dispatchTask().dispatch(TaskReference.parse(task),
                new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), CallingSession.UNNAMED, Actor.PRIMARY);
    }

    private static Map<Path, String> filesOf(Path checkout) throws IOException {
        try (Stream<Path> files = Files.walk(checkout)) {
            Map<Path, String> contents = new TreeMap<>();
            for (Path file : files.filter(Files::isRegularFile).filter(file -> !checkout.relativize(file).startsWith(".git")).toList()) {
                contents.put(checkout.relativize(file), Files.readString(file));
            }
            contents.put(Path.of(".git/index"), HexFormat.of().formatHex(Files.readAllBytes(checkout.resolve(".git/index"))));
            return contents;
        }
    }

    private static HookAnswer guarded(TestHome home, DispatchOutcome.Launched launched, String command) {
        return home.guardToolCall().checkForWorker(launched.worker().id(), new ToolCall("Bash", command, null, launched.worktree()));
    }

    private static void assertRefusedBeforeGitRuns(HookAnswer answer, String reason) {
        assertThat(answer.verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
        assertThat(answer.reason()).contains("skips the commit-msg hook", reason);
    }

    private static String credentialAnsweredIn(Path worktree) throws IOException, InterruptedException {
        ProcessBuilder fill = new ProcessBuilder("git", "credential", "fill").directory(worktree.toFile()).redirectErrorStream(true);
        fill.environment().keySet().removeIf(variable -> variable.startsWith("GIT_CONFIG_"));
        Process credential = fill.start();
        credential.getOutputStream().write("protocol=https\nhost=forge.example.org\n\n".getBytes(StandardCharsets.UTF_8));
        credential.getOutputStream().close();
        String answer = new String(credential.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        credential.waitFor(10, TimeUnit.SECONDS);
        return answer;
    }

    private static String resource(String name) throws IOException {
        try (InputStream content = WorkIsolationAndGitHygieneTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
