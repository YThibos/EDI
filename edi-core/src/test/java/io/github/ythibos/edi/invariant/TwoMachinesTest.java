package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.ythibos.edi.adapter.home.TimeSettings;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.dispatch.WorktreeHooks;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.PrePushHook;
import io.github.ythibos.edi.testing.PrePushHook.HookRun;
import io.github.ythibos.edi.testing.PrePushHook.RunningHook;
import io.github.ythibos.edi.testing.SyntheticSecrets;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@DisplayName("4.8 Two machines")
class TwoMachinesTest {

    private static final String REGISTERED = "https://github.com/example-owner/harness.git";
    private static final String DENIED_HOST = "acme-corp.example";
    private static final String TOKEN = SyntheticSecrets.githubToken();
    private static final String EMPLOYERS_PROJECT_AND_EDI = """
            projects:
              - name: service
                tracker: { kind: jira, projectKey: SVC }
                forge: { kind: gitlab, host: forge.example.org, path: team/service, account: keeper }
                base: main
              - name: EDI
                tracker: { kind: edi, projectKey: EDI }
                forge: { kind: github, path: example-owner/EDI, account: example-owner }
                base: main
            """;
    private static final String EMPLOYERS_NAMES = """
            forge.example.org
            keeper
            tracker.example.org
            tracker-keys.yml
            deckhand
            """;
    private static final String NOTES_NAMING_THE_EMPLOYERS_OWN_NAMES = """
            Clone https://forge.example.org/team/service as keeper.
            Tickets live on https://tracker.example.org/browse/SVC-7.
            The access token sits in tracker-keys.yml.
            """;

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-37 One codebase, per-machine choices")
    class OneCodebasePerMachineChoices {

        @Test
        @DisplayName("AT-37 the full test suite runs green under both the laptop and desktop fixture profiles")
        void theSuitePassesUnderBothProfiles() throws IOException {
            assertThat(System.getProperty(MachineFixture.PROPERTY)).as("the profile this run of the suite is under").isIn("laptop", "desktop");
            TestHome home = TestHome.in(directory);
            assertThat(Files.readString(home.home().machineProfile())).isEqualTo(MachineFixture.current().profile());
        }

        @Test
        @DisplayName("AT-37 the two fixture profiles differ in the tracker, the ledger and the worked line, and nothing else decides by machine")
        void theProfilesDifferOnlyInWhatTheMachineHolds() throws IOException {
            TestHome laptop = TestHome.in(directory.resolve("laptop")).on(MachineFixture.LAPTOP);
            TestHome desktop = TestHome.in(directory.resolve("desktop")).on(MachineFixture.DESKTOP);

            assertThat(laptop.machineProfile().tracker().kind()).isEqualTo(TrackerKind.JIRA);
            assertThat(laptop.machineProfile().time()).isEqualTo(new TimeSettings(true, true));
            assertThat(desktop.machineProfile().tracker().kind()).isEqualTo(TrackerKind.NONE);
            assertThat(desktop.machineProfile().time()).isEqualTo(new TimeSettings(false, false));
            assertThat(laptop.machineProfile().workers()).isEqualTo(desktop.machineProfile().workers());
        }
    }

    @Nested
    @DisplayName("I-38 No secret and no personal material is ever published")
    class NothingPersonalIsPublished {

        @RegisterExtension
        private final TestProcesses processes = new TestProcesses();

        @ParameterizedTest(name = "{0}")
        @EnumSource(Leak.class)
        @DisplayName("AT-38 fixture branches with a secret, a deny-listed term or a personal path are refused at edi deliver and at the worker's push")
        @Tag("adapter")
        void deliveryAndPushRefuseEachKindOfLeak(Leak leak) throws IOException, InterruptedException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeWithTheDenyList(directory);
            commitWork(home, leak.task(), leak);
            RunningHook push = pushStartedFor(home, leak.task(), processes);

            assertThatThrownBy(() -> home.deliverDraft().deliver(TaskReference.parse(leak.task()), Actor.PRIMARY))
                    .isInstanceOf(Refusal.class).hasMessageContaining("The publish check refuses").hasMessageNotContaining(TOKEN);
            HookRun refused = push.finished();
            assertThat(refused.refused()).isTrue();
            assertThat(refused.output()).contains("Refused: the outgoing commits carry a secret, personal material or a -SNAPSHOT version.")
                    .doesNotContain(TOKEN).doesNotContain(DENIED_HOST);
            assertThat(home.forge().openedDrafts()).isEmpty();
        }

        @Test
        @DisplayName("AT-38 a fixture branch without a secret or personal material passes edi deliver and the worker's push")
        @Tag("adapter")
        void deliveryAndPushPassAClearBranch() throws IOException, InterruptedException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeWithTheDenyList(directory);
            commitWork(home, "no-leak", copy -> copy.write("lamp.txt", "brass\n"));
            RunningHook push = pushStartedFor(home, "no-leak", processes);

            assertThat(home.deliverDraft().deliver(TaskReference.parse("no-leak"), Actor.PRIMARY).opened()).isTrue();
            HookRun passed = push.finished();
            assertThat(passed.refused()).as(passed.output()).isFalse();
            assertThat(home.forge().openedDrafts()).singleElement().satisfies(draft -> assertThat(draft.title()).isEqualTo("HRN-3 Work on no-leak"));
        }

        @Test
        @DisplayName("AT-38 an employer's project may push its own forge host, account, tracker host and keys file")
        @Tag("adapter")
        void aProjectMayPublishItsOwnNames() throws IOException, InterruptedException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeOfAnEmployersProjectAndEdi(directory);

            HookRun push = pushOfNotes(home, "service", "SVC-7", NOTES_NAMING_THE_EMPLOYERS_OWN_NAMES, processes);

            assertThat(push.refused()).as(push.output()).isFalse();
        }

        @Test
        @DisplayName("AT-38 EDI's own push is refused the employer's forge host, account, tracker host and keys file")
        @Tag("adapter")
        void edisPushMayNotPublishTheEmployersNames() throws IOException, InterruptedException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeOfAnEmployersProjectAndEdi(directory);

            HookRun push = pushOfNotes(home, "EDI", "EDI-7", NOTES_NAMING_THE_EMPLOYERS_OWN_NAMES, processes);

            assertThat(push.refused()).isTrue();
            assertThat(push.output()).contains("deny-list entry 1", "deny-list entry 2", "deny-list entry 3", "deny-list entry 4")
                    .doesNotContain("deny-list entry 5", "forge.example.org", "keeper", "tracker.example.org", "tracker-keys.yml");
        }

        @Test
        @DisplayName("AT-38 an employer's project is still refused another account's name beside its own names")
        @Tag("adapter")
        void anotherAccountIsStillRefused() throws IOException, InterruptedException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeOfAnEmployersProjectAndEdi(directory);

            HookRun push = pushOfNotes(home, "service", "SVC-8", NOTES_NAMING_THE_EMPLOYERS_OWN_NAMES + "Ask deckhand for the review.\n", processes);

            assertThat(push.refused()).isTrue();
            assertThat(push.output()).contains("docs/notes.md:4 in commit").contains("deny-list entry 5")
                    .doesNotContain("deny-list entry 1", "deny-list entry 2", "deny-list entry 3", "deny-list entry 4", "deckhand");
        }

        @Test
        @DisplayName("AT-38 the publish check in an employer's project clone clears its own forge host, account, tracker host and keys file")
        @Tag("adapter")
        void aProjectsCloneMayPublishItsOwnNames() throws IOException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeOfAnEmployersProjectAndEdi(directory);

            PublishVerdict verdict = publishCheckOfNotesIn(home, "service", "SVC-10", NOTES_NAMING_THE_EMPLOYERS_OWN_NAMES);

            assertThat(verdict.findings()).isEmpty();
        }

        @Test
        @DisplayName("AT-38 the publish check in EDI's clone refuses the employer's forge host, account, tracker host and keys file")
        @Tag("adapter")
        void edisCloneMayNotPublishTheEmployersNames() throws IOException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeOfAnEmployersProjectAndEdi(directory);

            PublishVerdict verdict = publishCheckOfNotesIn(home, "EDI", "EDI-10", NOTES_NAMING_THE_EMPLOYERS_OWN_NAMES);

            assertThat(verdict.allowsPush()).isFalse();
            assertThat(verdict.findings().toString()).contains("deny-list entry 1", "deny-list entry 2", "deny-list entry 3", "deny-list entry 4")
                    .doesNotContain("deny-list entry 5", "forge.example.org", "keeper", "tracker.example.org", "tracker-keys.yml");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"service, SVC-9", "EDI, EDI-9"})
        @DisplayName("AT-38 a secret is still refused from an employer's project and from EDI alike")
        @Tag("adapter")
        void aSecretIsStillRefusedFromEitherProject(String project, String key) throws IOException, InterruptedException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = homeOfAnEmployersProjectAndEdi(directory);
            String token = SyntheticSecrets.githubToken();

            HookRun push = pushOfNotes(home, project, key, "forge.token=" + token + "\n", processes);

            assertThat(push.refused()).isTrue();
            assertThat(push.output()).contains("docs/notes.md:1 in commit").doesNotContain("deny-list entry").doesNotContain(token);
        }
    }

    @Nested
    @DisplayName("I-39 Publication targets are declared, never inferred")
    class PublicationTargetsAreDeclared {

        @RegisterExtension
        private final TestProcesses processes = new TestProcesses();

        @Test
        @DisplayName("AT-43 a worktree whose origin points at another repository is refused at push and at edi deliver")
        @Tag("adapter")
        void aChangedOriginIsRefused() throws IOException, InterruptedException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            FixtureRepository copy = publishedCopy(home, "fit-lamp", "HRN-3");
            copy.write("lamp.txt", "brass\n").commit("HRN-3 Fit the lamp");
            String elsewhere = "https://someone:" + SyntheticSecrets.githubToken() + "@github.com/someone-else/harness.git";
            copy.run("config", "--worktree", "remote.origin.pushurl", elsewhere);
            home.forge().pushed(BranchName.parse("feature/HRN-3"), copy.head());

            assertThatThrownBy(() -> home.deliverDraft().deliver(TaskReference.parse("fit-lamp"), Actor.PRIMARY))
                    .isInstanceOf(Refusal.class)
                    .hasMessage("This work is published only to https://github.com/example-owner/harness, the repository its project registers,"
                            + " and the push goes to github.com/someone-else/harness.");
            HookRun push = PrePushHook.pushes(copy, home.home().root(), "feature/HRN-3", elsewhere, processes);
            assertThat(push.refused()).isTrue();
            assertThat(push.output()).contains("This work is published only to https://github.com/example-owner/harness")
                    .doesNotContain(elsewhere.substring(elsewhere.indexOf(':', 6) + 1, elsewhere.indexOf('@')));
            assertThat(home.forge().openedDrafts()).isEmpty();
            assertThat(home.deliveries().of(new TaskId("fit-lamp"))).isEmpty();
        }
    }

    private static TestHome homeWithTheDenyList(Path directory) throws IOException {
        return WorkerFixtures.homeWithProjects(directory).writing("config/publish-denylist", DENIED_HOST + "\n");
    }

    private static void commitWork(TestHome home, String task, Work work) throws IOException {
        FixtureRepository copy = publishedCopy(home, task, "HRN-3");
        work.into(copy);
        copy.commit("HRN-3 Add the work");
        home.forge().pushed(BranchName.parse(branchOf(home, task)), copy.head());
    }

    private static FixtureRepository publishedCopy(TestHome home, String task, String key) throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip(task, "harness", key)));
        DispatchOutcome dispatched = home.dispatchTask().dispatch(TaskReference.parse(task),
                new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), CallingSession.UNNAMED, Actor.PRIMARY);
        FixtureRepository copy = FixtureRepository.at(dispatched.launched().worktree());
        copy.run("config", "--worktree", "remote.origin.pushurl", REGISTERED);
        return copy;
    }

    private static String branchOf(TestHome home, String task) {
        return home.dispatches().of(new TaskId(task)).orElseThrow().branch().toString();
    }

    private static RunningHook pushStartedFor(TestHome home, String task, TestProcesses processes) throws IOException {
        return PrePushHook.starts(copyOf(home, task), home.home().root(), branchOf(home, task), REGISTERED, processes);
    }

    private static FixtureRepository copyOf(TestHome home, String task) {
        return FixtureRepository.at(home.home().worktreeOf(new ProjectName("harness"), new TaskId(task)));
    }

    private static TestHome homeOfAnEmployersProjectAndEdi(Path directory) throws IOException {
        TestHome home = TestHome.in(directory.resolve("home")).on(MachineFixture.LAPTOP)
                .registering(EMPLOYERS_PROJECT_AND_EDI)
                .writing("config/publish-denylist", EMPLOYERS_NAMES);
        WorkerFixtures.cloneOf(directory, home, "service");
        WorkerFixtures.cloneOf(directory, home, "EDI");
        return home;
    }

    private static HookRun pushOfNotes(TestHome home, String project, String key, String notes, TestProcesses processes)
            throws IOException, InterruptedException {
        ProjectEntry entry = home.registry().entryOf(new ProjectName(project));
        FixtureRepository copy = copyWithItsHooks(home, entry, BranchName.parse("feature/" + key));
        copy.write("docs/notes.md", notes).commit(key + " Write the notes");
        return PrePushHook.pushes(copy, home.home().root(), "feature/" + key, entry.requireForge().repository().url() + ".git", processes);
    }

    private static FixtureRepository copyWithItsHooks(TestHome home, ProjectEntry project, BranchName branch) throws IOException {
        Path copy = home.home().worktreeOf(project.project().name(), new TaskId(branch.workKey().toString().toLowerCase(Locale.ROOT)));
        FixtureRepository.at(project.projectClone()).run("worktree", "add", "--quiet", "-b", branch.toString(), copy.toString(), "origin/main");
        new WorktreeHooks(home.git()).installIn(project, copy, branch, project.requireBase());
        return FixtureRepository.at(copy);
    }

    private static PublishVerdict publishCheckOfNotesIn(TestHome home, String project, String key, String notes) throws IOException {
        Path clone = home.registry().entryOf(new ProjectName(project)).projectClone();
        FixtureRepository.at(clone).write("docs/notes.md", notes).commit(key + " Write the notes");
        return home.publishCheck().check(clone, new RevisionRange("HEAD~1..HEAD"));
    }

    @FunctionalInterface
    private interface Work {

        void into(FixtureRepository copy) throws IOException;
    }

    private enum Leak implements Work {

        SECRET("leak-secret") {
            @Override
            public void into(FixtureRepository copy) throws IOException {
                copy.write("config/app.properties", "forge.token=" + TOKEN + "\n");
            }
        },
        DENY_LISTED_TERM("leak-term") {
            @Override
            public void into(FixtureRepository copy) throws IOException {
                copy.write("docs/mirror.md", "Clone from https://git." + DENIED_HOST + "/team/harness\n");
            }
        },
        PERSONAL_PATH("leak-path") {
            @Override
            public void into(FixtureRepository copy) throws IOException {
                copy.write("personal/notes.md", "my own notes\n").forceAdd("personal/notes.md");
            }
        };

        private final String task;

        Leak(String task) {
            this.task = task;
        }

        String task() {
            return task;
        }
    }
}
