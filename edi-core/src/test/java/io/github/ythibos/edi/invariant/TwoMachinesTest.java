package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.ythibos.edi.adapter.home.TimeSettings;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.dispatch.WorktreeHooks;
import io.github.ythibos.edi.domain.Refusal;
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

        @Test
        @DisplayName("AT-38 fixture branches with a secret, a deny-listed term or a personal path are refused at edi deliver and at the worker's push")
        @Tag("adapter")
        void deliveryAndPushRefuseEachKindOfLeak() throws IOException, InterruptedException {
            assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
            TestHome home = WorkerFixtures.homeWithProjects(directory).writing("config/publish-denylist", DENIED_HOST + "\n");
            String token = SyntheticSecrets.githubToken();
            Map<String, Leak> leaks = new LinkedHashMap<>();
            leaks.put("leak-secret", copy -> copy.write("config/app.properties", "forge.token=" + token + "\n"));
            leaks.put("leak-term", copy -> copy.write("docs/mirror.md", "Clone from https://git." + DENIED_HOST + "/team/harness\n"));
            leaks.put("leak-path", copy -> copy.write("personal/notes.md", "my own notes\n").forceAdd("personal/notes.md"));
            leaks.put("no-leak", copy -> copy.write("lamp.txt", "brass\n"));
            int key = 3;
            for (Map.Entry<String, Leak> leak : leaks.entrySet()) {
                FixtureRepository copy = publishedCopy(home, leak.getKey(), "HRN-" + key++);
                leak.getValue().into(copy);
                copy.commit(copy.run("rev-parse", "--abbrev-ref", "HEAD").strip().replace("feature/", "") + " Add the work");
                home.forge().pushed(BranchName.parse(copy.run("rev-parse", "--abbrev-ref", "HEAD").strip()), copy.head());
            }
            Map<String, RunningHook> pushes = pushesStartedFor(home, leaks.keySet(), processes);

            for (String leaking : List.of("leak-secret", "leak-term", "leak-path")) {
                assertThatThrownBy(() -> home.deliverDraft().deliver(TaskReference.parse(leaking), Actor.PRIMARY))
                        .as(leaking).isInstanceOf(Refusal.class).hasMessageContaining("The publish check refuses").hasMessageNotContaining(token);
                HookRun push = pushes.get(leaking).finished();
                assertThat(push.refused()).as(leaking).isTrue();
                assertThat(push.output()).as(leaking).contains("Refused: the outgoing commits carry a secret or personal material.")
                        .doesNotContain(token).doesNotContain(DENIED_HOST);
            }
            assertThat(home.deliverDraft().deliver(TaskReference.parse("no-leak"), Actor.PRIMARY).opened()).isTrue();
            HookRun cleanPush = pushes.get("no-leak").finished();
            assertThat(cleanPush.refused()).as(cleanPush.output()).isFalse();
            assertThat(home.forge().openedDrafts()).singleElement().satisfies(draft -> assertThat(draft.title()).isEqualTo("HRN-6 Work on no-leak"));
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
                    .hasMessage("EDI publishes this work only to https://github.com/example-owner/harness, the repository its project registers,"
                            + " and the push goes to github.com/someone-else/harness.");
            HookRun push = PrePushHook.pushes(copy, home.home().root(), "feature/HRN-3", elsewhere, processes);
            assertThat(push.refused()).isTrue();
            assertThat(push.output()).contains("EDI publishes this work only to https://github.com/example-owner/harness")
                    .doesNotContain(elsewhere.substring(elsewhere.indexOf(':', 6) + 1, elsewhere.indexOf('@')));
            assertThat(home.forge().openedDrafts()).isEmpty();
            assertThat(home.deliveries().of(new TaskId("fit-lamp"))).isEmpty();
        }
    }

    private static FixtureRepository publishedCopy(TestHome home, String task, String key) throws IOException {
        home.storing(List.of(WorkerFixtures.queuedShip(task, "harness", key)));
        DispatchOutcome dispatched = home.dispatchTask().dispatch(TaskReference.parse(task),
                new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), Actor.PRIMARY);
        FixtureRepository copy = FixtureRepository.at(dispatched.launched().worktree());
        copy.run("config", "--worktree", "remote.origin.pushurl", REGISTERED);
        return copy;
    }

    private static Map<String, RunningHook> pushesStartedFor(TestHome home, Set<String> tasks, TestProcesses processes) throws IOException {
        Map<String, RunningHook> pushes = new LinkedHashMap<>();
        for (String task : tasks) {
            pushes.put(task, PrePushHook.starts(copyOf(home, task), home.home().root(), branchOf(home, task), REGISTERED, processes));
        }
        return pushes;
    }

    private static FixtureRepository copyOf(TestHome home, String task) {
        return FixtureRepository.at(home.home().worktreeOf(new ProjectName("harness"), new TaskId(task)));
    }

    private static String branchOf(TestHome home, String task) {
        return home.dispatches().of(new TaskId(task)).orElseThrow().branch().toString();
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
    private interface Leak {

        void into(FixtureRepository copy) throws IOException;
    }
}
