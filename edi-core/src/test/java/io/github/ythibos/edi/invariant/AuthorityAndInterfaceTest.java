package io.github.ythibos.edi.invariant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import io.github.ythibos.edi.adapter.forge.ForgeCalls;
import io.github.ythibos.edi.adapter.forge.ForgeCommand;
import io.github.ythibos.edi.adapter.forge.GitHubForge;
import io.github.ythibos.edi.adapter.forge.GitLabForge;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.application.brief.WorkerBriefs;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.port.Forge;
import io.github.ythibos.edi.architecture.HandWrittenClasses;
import io.github.ythibos.edi.architecture.NameAMergeOperation;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BriefFixtures;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.ForgeStandIns;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.NotPermitted;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("4.1 Authority and interface")
class AuthorityAndInterfaceTest {

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("I-01 One interface")
    class OneInterface {

        @Test
        @DisplayName("AT-01a the generated brief contains the rule and no worker-facing text addresses the captain")
        void theBriefCarriesTheRuleAndNeverAddressesTheCaptain() throws IOException {
            WorkerBriefs briefs = new WorkerBriefs();
            List<String> briefTexts = BriefFixtures.everyKindOfBrief().map(briefs::briefFor).toList();
            String notice = InboxMessage.sent(new TaskId("fit-lamp"), 1, "Use the brass lamp.", null, TestHome.START).notice();

            assertThat(briefTexts).allSatisfy(brief -> assertThat(brief).contains("never address the captain"));
            assertThat(Stream.concat(briefTexts.stream(), Stream.of(resource("claude/worker/system-prompt.md"), notice)))
                    .flatMap(text -> text.lines().toList())
                    .noneMatch(AuthorityAndInterfaceTest::addressesTheCaptain);
        }

        @Test
        @DisplayName("AT-01b edi status rejects text that begins with the address word or 'Captain,' and tells the worker why")
        void statusRejectsTextAddressedToTheCaptain() throws IOException {
            TestHome home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
            Worker worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);

            for (String addressed : List.of("Commander, the lamp is fitted.", "captain, the lamp is fitted.", "  CAPTAIN, done.")) {
                assertThatThrownBy(() -> home.recordStatus().report(worker.id(), StatusState.DONE, null, null, addressed))
                        .isInstanceOf(Refusal.class)
                        .hasMessageContaining("never addresses the captain")
                        .hasMessageContaining("\"Commander,\"");
            }
            home.writing("config/personal.yaml", "identity:\n  address: Skipper\n");
            assertThatThrownBy(() -> home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Skipper, starting now."))
                    .isInstanceOf(Refusal.class).hasMessageContaining("\"Skipper,\"");
            assertThat(home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "Fitting the lamp.").lastStatus().report().text())
                    .isEqualTo("Fitting the lamp.");
        }
    }

    @Nested
    @DisplayName("I-02 The captain's current session is primary")
    class TheCaptainsSessionIsPrimary {

        @Test
        @DisplayName("AT-02 a typed prompt in a second session moves the primary role and the first session's mutating calls are refused")
        void aTypedPromptMovesThePrimaryRole() throws IOException {
            TestHome home = TestHome.in(directory);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            home.primaryHooks().sessionStarted(first, null);
            home.primaryHooks().promptSubmitted(first, "What is waiting on me?");
            home.primaryHooks().sessionStarted(second, null);

            assertThat(standingOf(home, first)).isEqualTo(Standing.PRIMARY);
            assertThat(standingOf(home, second)).isEqualTo(Standing.STANDBY);

            home.primaryHooks().promptSubmitted(second, "Fit the brass lamp.");
            home.primaryHooks().promptSubmitted(first, "<task-notification><status>completed</status></task-notification>");

            assertThat(standingOf(home, second)).isEqualTo(Standing.PRIMARY);
            assertThat(standingOf(home, first)).isEqualTo(Standing.STANDBY);
            assertThatThrownBy(() -> callerOf(home, first).requirePrimary("edi task add"))
                    .isInstanceOf(NotPermitted.class)
                    .hasMessageStartingWith("This session is standby")
                    .hasMessageContaining("edi task add is refused here");
            callerOf(home, second).requirePrimary("edi task add");
        }

        private static Standing standingOf(TestHome home, UUID session) {
            return home.primarySessions().standingOf(session);
        }

        private static Caller callerOf(TestHome home, UUID session) {
            return Caller.holdingTheHomeToken(session, standingOf(home, session));
        }
    }

    @Nested
    @DisplayName("I-03 No merge capability")
    class NoMergeCapability {

        private static final JavaClasses CORE = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(new HandWrittenClasses())
                .importPackages("io.github.ythibos.edi");

        @Test
        @DisplayName("AT-03a no adapter method or HTTP call names merge, auto-merge or merge_when_pipeline_succeeds")
        void noAdapterMethodOrHttpCallNamesAMerge() {
            noClasses().that().resideInAnyPackage("io.github.ythibos.edi.adapter..", "io.github.ythibos.edi.web..")
                    .should(new NameAMergeOperation())
                    .check(CORE);
            List<String> endpoints = CORE.stream().flatMap(AuthorityAndInterfaceTest::endpointsOf).toList();

            assertThat(endpoints).contains("/api/tasks/{reference}/deliver", "/api/merged", "/api/tasks/{reference}/cleanup", "/api/publish-checks");
            assertThat(endpoints).flatMap(endpoint -> List.of(endpoint.split("/")))
                    .filteredOn(segment -> !segment.startsWith("{"))
                    .noneMatch(NameAMergeOperation::namesAMergeOperation);
        }

        @Test
        @DisplayName("AT-03b the forge command allow-lists hold no merge command and no push to a base branch")
        @Tag("adapter")
        void theForgeAllowListsHoldNoMerge() throws IOException {
            assertThat(ForgeCommand.values()).allSatisfy(command -> assertThat(command.words()).noneMatch(AuthorityAndInterfaceTest::mergesOrPushes));
            noClasses().that().resideInAPackage("io.github.ythibos.edi.adapter.forge..").and().doNotBelongToAnyOf(ForgeCalls.class)
                    .should().dependOnClassesThat().belongToAnyOf(ExternalPrograms.class, ProgramCall.class)
                    .because("every gh and glab call runs through ForgeCalls, which runs only the allow-listed forge commands")
                    .check(CORE);

            ForgeStandIns standIns = everyForgeAnswerIn(directory.resolve("forge-tools"));
            ForgeCalls calls = new ForgeCalls(new ExternalPrograms(), standIns.gh(), standIns.glab());
            exerciseEveryCallOf(new GitHubForge(calls, new JsonMapper()), GITHUB_PROJECT);
            exerciseEveryCallOf(new GitLabForge(calls, new JsonMapper()), GITLAB_PROJECT);

            assertThat(standIns.calls()).hasSizeGreaterThan(ForgeCommand.values().length - 1)
                    .allSatisfy(call -> assertThat(call.split(" ")).noneMatch(AuthorityAndInterfaceTest::mergesOrPushes));
            assertThat(standIns.calls()).filteredOn(call -> call.startsWith("pr create") || call.startsWith("mr create"))
                    .hasSize(2).allSatisfy(call -> assertThat(call).contains("--draft"));
        }

        private static ForgeStandIns everyForgeAnswerIn(Path place) throws IOException {
            String pull = """
                    {"number":7,"url":"https://github.com/example-owner/harness/pull/7","state":"OPEN","isDraft":true,"headRefName":"feature/HRN-3",
                     "baseRefName":"main","headRefOid":"1111111111111111111111111111111111111111","mergeCommit":null}""";
            String request = """
                    {"iid":5,"web_url":"https://forge.example.org/team/harness/-/merge_requests/5","state":"opened","draft":true,
                     "source_branch":"feature/HRN-3","target_branch":"main","sha":"2222222222222222222222222222222222222222"}""";
            return ForgeStandIns.in(place)
                    .answering("pr-list", "[" + pull + "]")
                    .answering("pr-view", pull)
                    .answering("pr-create", "https://github.com/example-owner/harness/pull/7\n")
                    .answering("auth-status", "{\"hosts\":{\"github.com\":[{\"login\":\"example-owner\",\"scopes\":\"repo, workflow\"}]}}")
                    .answering("api-user", "{\"username\":\"example-owner\"}")
                    .answering("mr-list", "[" + request + "]")
                    .answering("mr-view", request)
                    .answering("mr-create", "https://forge.example.org/team/harness/-/merge_requests/5\n");
        }

        private static void exerciseEveryCallOf(Forge forge, RegisteredForge project) {
            BranchName branch = BranchName.parse("feature/HRN-3");
            MergeRequestView open = forge.openMergeRequestFrom(project, branch).orElseThrow();
            forge.view(project, open.address());
            forge.openDraft(project, DraftProposal.forWork(branch, new BaseBranch("main"), "Fit the lamp", "The brass lamp."));
            forge.mayChangeWorkflows(project);
        }
    }

    @Nested
    @DisplayName("I-04 Drafts only")
    class DraftsOnly {

        @Test
        @DisplayName("AT-04 delivery refuses a pipeline outcome whose merge request is not a draft and records a failure")
        @Tag("adapter")
        void deliveryRefusesANonDraft() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
            DispatchOutcome dispatched = home.dispatchTask().dispatch(TaskReference.parse("fit-lamp"),
                    new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), Actor.PRIMARY);
            Worker worker = dispatched.launched().worker();
            String head = FixtureRepository.at(dispatched.launched().worktree()).write("lamp.txt", "brass\n").commit("HRN-3 Fit the lamp").head();
            RegisteredForge forge = home.registry().entryOf(new ProjectName("harness")).requireForge();
            MergeRequestView ready = home.forge().opensAReadyMergeRequest(forge, dispatched.launched().branch(), new BaseBranch("main"), head);

            assertThatThrownBy(() -> home.recordStatus().report(worker.id(), StatusState.DONE, null, null, ready.address().url()))
                    .isInstanceOf(Refusal.class)
                    .hasMessageContaining(ready.address() + " is not a draft");
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("DeliveryRefused");
            assertThat(home.deliveries().of(new TaskId("fit-lamp"))).isEmpty();
            assertThat(home.workers().get(worker.id()).orElseThrow().lastStatus()).isNull();

            home.forge().opensADraft(forge, dispatched.launched().branch(), new BaseBranch("main"), head);
            home.recordStatus().report(worker.id(), StatusState.DONE, null, null, ready.address().url());

            assertThat(home.deliveries().of(new TaskId("fit-lamp"))).hasValueSatisfying(delivery -> assertThat(delivery.draft()).isTrue());
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("StatusReported", "DraftRecorded");
        }
    }

    @Nested
    @DisplayName("I-05 A report authorises nothing")
    class AReportAuthorisesNothing {

        @Test
        @DisplayName("AT-05 edi task promote refuses a scout without a recorded captain authorisation")
        void promotionNeedsTheCaptainsAuthorisation() {
            Pending.until(Slice.S8);
        }
    }

    @Nested
    @DisplayName("I-06 Destructive, irreversible and security-sensitive actions need the captain's word in the current exchange")
    class DestructiveActionsNeedTheCaptainsWord {

        @Test
        @DisplayName("AT-06 discarding a worktree or work, deleting an unlanded branch and touching secrets need a decision answered this session")
        void destructiveCommandsNeedAnAnswerFromThisSession() {
            Pending.until(Slice.S8);
        }
    }

    private static final RegisteredForge GITHUB_PROJECT = new RegisteredForge(
            ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"), "example-owner");
    private static final RegisteredForge GITLAB_PROJECT = new RegisteredForge(
            ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org", "team/harness"), "example-owner");

    private static boolean mergesOrPushes(String word) {
        String bare = word.toLowerCase(Locale.ROOT).replaceFirst("^-+", "").replace('_', '-');
        return List.of("merge", "push", "fill").contains(bare)
                || bare.contains("auto-merge")
                || bare.contains("automerge")
                || bare.contains("merge-when-pipeline-succeeds");
    }

    private static Stream<String> endpointsOf(JavaClass controller) {
        List<String> prefixes = controller.tryGetAnnotationOfType(RequestMapping.class).map(mapping -> List.of(mapping.value())).orElse(List.of(""));
        return controller.getMethods().stream()
                .flatMap(method -> Stream.of(
                                method.tryGetAnnotationOfType(GetMapping.class).map(GetMapping::value),
                                method.tryGetAnnotationOfType(PostMapping.class).map(PostMapping::value),
                                method.tryGetAnnotationOfType(RequestMapping.class).map(RequestMapping::value))
                        .flatMap(Optional::stream))
                .flatMap(Stream::of)
                .flatMap(path -> prefixes.stream().map(prefix -> prefix + path));
    }

    private static boolean addressesTheCaptain(String line) {
        String opening = line.strip().toLowerCase(Locale.ROOT);
        return opening.startsWith("commander,") || opening.startsWith("captain,");
    }

    private static String resource(String name) throws IOException {
        try (InputStream content = AuthorityAndInterfaceTest.class.getClassLoader().getResourceAsStream(name)) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
