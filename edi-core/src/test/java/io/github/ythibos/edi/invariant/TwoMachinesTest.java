package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.home.TimeSettings;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.tracker.TrackerKind;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.MachineFixture;
import io.github.ythibos.edi.testing.PrePushHook;
import io.github.ythibos.edi.testing.PrePushHook.HookRun;
import io.github.ythibos.edi.testing.SyntheticSecrets;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@DisplayName("4.8 Two machines")
class TwoMachinesTest {

    private static final String REGISTERED = "https://github.com/example-owner/harness.git";
    private static final String DENIED_HOST = "acme-corp.example";

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

            for (String leaking : List.of("leak-secret", "leak-term", "leak-path")) {
                assertThatThrownBy(() -> home.deliverDraft().deliver(TaskReference.parse(leaking), Actor.PRIMARY))
                        .as(leaking).isInstanceOf(Refusal.class).hasMessageContaining("The publish check refuses").hasMessageNotContaining(token);
                HookRun push = PrePushHook.pushes(copyOf(home, leaking), home.home().root(), branchOf(home, leaking), REGISTERED, processes);
                assertThat(push.refused()).as(leaking).isTrue();
                assertThat(push.output()).as(leaking).contains("Refused: the outgoing commits carry a secret or personal material.")
                        .doesNotContain(token).doesNotContain(DENIED_HOST);
            }
            assertThat(home.deliverDraft().deliver(TaskReference.parse("no-leak"), Actor.PRIMARY).opened()).isTrue();
            HookRun cleanPush = PrePushHook.pushes(copyOf(home, "no-leak"), home.home().root(), branchOf(home, "no-leak"), REGISTERED, processes);
            assertThat(cleanPush.refused()).as(cleanPush.output()).isFalse();
            assertThat(home.forge().openedDrafts()).singleElement().satisfies(draft -> assertThat(draft.title()).isEqualTo("HRN-6 Work on no-leak"));
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

    private static FixtureRepository copyOf(TestHome home, String task) {
        return FixtureRepository.at(home.home().worktreeOf(new ProjectName("harness"), new TaskId(task)));
    }

    private static String branchOf(TestHome home, String task) {
        return home.dispatches().of(new TaskId(task)).orElseThrow().branch().toString();
    }

    @FunctionalInterface
    private interface Leak {

        void into(FixtureRepository copy) throws IOException;
    }
}
