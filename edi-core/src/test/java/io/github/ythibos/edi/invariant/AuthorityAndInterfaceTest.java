package io.github.ythibos.edi.invariant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import io.github.ythibos.edi.application.decision.GivenAnswer;
import io.github.ythibos.edi.application.delivery.FollowMerges.MergedWork;
import io.github.ythibos.edi.application.dispatch.DispatchOutcome;
import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.application.port.Forge;
import io.github.ythibos.edi.application.task.PromotedScout;
import io.github.ythibos.edi.application.worker.HookAnswer;
import io.github.ythibos.edi.application.worker.ToolCall;
import io.github.ythibos.edi.architecture.HandWrittenClasses;
import io.github.ythibos.edi.architecture.NameAMergeOperation;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.authority.CaptainWordAct;
import io.github.ythibos.edi.domain.board.Board;
import io.github.ythibos.edi.domain.board.BoardCard;
import io.github.ythibos.edi.domain.board.BoardFeedback;
import io.github.ythibos.edi.domain.board.BoardPrompt;
import io.github.ythibos.edi.domain.board.BoardRound;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.decision.AnswerChannel;
import io.github.ythibos.edi.domain.decision.DecisionCard;
import io.github.ythibos.edi.domain.decision.DecisionNumber;
import io.github.ythibos.edi.domain.decision.DecisionOption;
import io.github.ythibos.edi.domain.decision.OptionKey;
import io.github.ythibos.edi.domain.decision.Recommendation;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.PipelineRun;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.primary.SessionSource;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.Task;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.task.TaskKind;
import io.github.ythibos.edi.domain.task.TaskState;
import io.github.ythibos.edi.domain.worker.DoneCheck;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BoardRounds;
import io.github.ythibos.edi.testing.BriefFixtures;
import io.github.ythibos.edi.testing.Cards;
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
            home.primaryHooks().sessionStarted(first, null, SessionSource.LAUNCH);
            home.primaryHooks().promptSubmitted(first, "What is waiting on me?");
            home.primaryHooks().sessionStarted(second, null, SessionSource.LAUNCH);

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
            MergeRequestView open = forge.mergeRequestsFrom(project, branch).getFirst();
            forge.view(project, open.address());
            forge.openDraft(project, DraftProposal.forWork(branch, new BaseBranch("main"), "Fit the lamp", "The brass lamp."));
            forge.mayChangeWorkflows(project);
        }
    }

    @Nested
    @DisplayName("I-04 Drafts only")
    class DraftsOnly {

        private static final TaskId LAMP = new TaskId("fit-lamp");
        private static final TaskId HORN = new TaskId("fit-horn");

        @ParameterizedTest(name = "{0}")
        @EnumSource(ForgeKind.class)
        @DisplayName("AT-04a a request marked ready for review before the done is recorded from the worker's status and accepted at the done")
        @Tag("adapter")
        void aRequestReadyBeforeTheDoneIsAccepted(ForgeKind kind) throws IOException {
            TestHome home = WorkerFixtures.homeWithProjectsOn(directory, kind);
            Worker worker = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            committedIn(home, LAMP, "HRN-3", "lamp.txt");
            MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, worker);

            home.recordStatus().report(worker.id(), StatusState.WORKING, null, null, "The checks run on the draft.");
            assertThat(home.deliveries().of(LAMP)).hasValueSatisfying(delivery -> assertThat(delivery.draft()).isTrue());

            home.forge(kind).marksReady(draft.address());
            home.recordStatus().report(worker.id(), StatusState.DONE, null, null, draft.address().url());

            assertThat(home.workers().get(worker.id()).orElseThrow().doneCheck()).isEqualTo(DoneCheck.ACCEPTED);
            assertThat(home.eventTypesOf(LAMP)).containsOnlyOnce("DraftRecorded").doesNotContain("DeliveryRefused");
            assertThat(home.deliveries().of(LAMP)).hasValueSatisfying(delivery -> assertThat(delivery.draft()).isFalse());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ForgeKind.class)
        @DisplayName("AT-04b a request merged before the done is recorded at the done, its merge followed and its task cleaned up")
        @Tag("adapter")
        void aRequestMergedBeforeTheDoneIsFollowed(ForgeKind kind) throws IOException {
            TestHome home = WorkerFixtures.homeWithProjectsOn(directory, kind);
            Worker worker = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            FixtureRepository copy = committedIn(home, LAMP, "HRN-3", "lamp.txt");
            MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, worker);
            home.forge(kind).marksReady(draft.address());
            home.forge(kind).merges(draft.address(), landedOnMain(copy));
            assertThat(home.deliveries().of(LAMP)).isEmpty();

            home.recordStatus().report(worker.id(), StatusState.DONE, null, null, draft.address().url());

            assertThat(home.deliveries().of(LAMP)).hasValueSatisfying(delivery -> assertThat(delivery.isMerged()).isTrue());
            assertThat(home.eventTypesOf(LAMP))
                    .containsSubsequence("StatusReported", "DraftRecorded", "DraftMerged", "CloneRefreshed", "TaskCleanedUp");
            assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
            assertThat(copy.root()).doesNotExist();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ForgeKind.class)
        @DisplayName("AT-04c a request merged before the pipeline finished is recorded from the pipeline's run, and the merge hands the work over")
        @Tag("adapter")
        void aRequestMergedBeforeThePipelineFinishedIsFollowed(ForgeKind kind) throws IOException {
            TestHome home = WorkerFixtures.homeWithProjectsOn(directory, kind);
            Worker worker = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            FixtureRepository copy = committedIn(home, LAMP, "HRN-3", "lamp.txt");
            BranchName branch = home.dispatches().of(LAMP).orElseThrow().branch();
            home.pipeline().runs(branch, new PipelineRun("01RUN", "running", "review", copy.head(), null));
            home.observePipelines().observe();
            MergeRequestView draft = WorkerFixtures.opensTheDraftOf(home, worker);
            home.forge(kind).merges(draft.address(), landedOnMain(copy));

            home.pipeline().runs(branch, new PipelineRun("01RUN", "completed", null, copy.head(), draft.address().url()));
            home.observePipelines().observe();

            assertThat(home.eventTypesOf(LAMP)).containsSubsequence("PipelineRunSeen", "PipelineRunFinished", "DraftRecorded", "DraftMerged",
                    "CloneRefreshed", "TaskCleanedUp");
            assertThat(home.tasks().get(LAMP).state()).isEqualTo(TaskState.DONE);
            assertThat(home.runtime().isRunning(worker.window())).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ForgeKind.class)
        @DisplayName("AT-04d edi merged follows a merge EDI never recorded, by task and by address, while the idle worker still runs")
        @Tag("adapter")
        void theCaptainsWordFollowsAMergeEdiNeverRecorded(ForgeKind kind) throws IOException {
            TestHome home = WorkerFixtures.homeWithProjectsOn(directory, kind);
            Worker lamp = WorkerFixtures.dispatchedShip(home, "fit-lamp", "HRN-3");
            FixtureRepository lampCopy = committedIn(home, LAMP, "HRN-3", "lamp.txt");
            MergeRequestView lampRequest = home.forge(kind).marksReady(WorkerFixtures.opensTheDraftOf(home, lamp).address());
            home.forge(kind).merges(lampRequest.address(), landedOnMain(lampCopy));
            Worker horn = WorkerFixtures.dispatchedShip(home, "fit-horn", "HRN-4");
            FixtureRepository hornCopy = committedIn(home, HORN, "HRN-4", "horn.txt");
            MergeRequestView hornRequest = WorkerFixtures.opensTheDraftOf(home, horn);
            home.forge(kind).merges(hornRequest.address(), landedOnMain(hornCopy));

            MergedWork byTask = home.followMerges().mergedSaysTheCaptain("fit-lamp", Actor.PRIMARY);
            MergedWork byAddress = home.followMerges().mergedSaysTheCaptain(hornRequest.address().url(), Actor.PRIMARY);

            assertThat(byTask.delivery().address()).isEqualTo(lampRequest.address());
            assertThat(byAddress.delivery().address()).isEqualTo(hornRequest.address());
            assertThat(List.of(byTask, byAddress)).allSatisfy(merged -> assertThat(merged.cleanUp().done()).isTrue());
            assertThat(List.of(LAMP, HORN)).allSatisfy(task -> assertThat(home.tasks().get(task).state()).isEqualTo(TaskState.DONE));
        }

        private FixtureRepository committedIn(TestHome home, TaskId task, String key, String file) throws IOException {
            return FixtureRepository.at(home.home().worktreeOf(new ProjectName("harness"), task)).write(file, "brass\n").commit(key + " Fit " + file);
        }

        private String landedOnMain(FixtureRepository copy) throws IOException {
            FixtureRepository upstream = FixtureRepository.at(directory.resolve("upstream/harness"));
            upstream.run("fetch", "--quiet", copy.root().toString(), "HEAD");
            upstream.run("merge", "--quiet", "--squash", "FETCH_HEAD");
            return upstream.commit("Land " + copy.root().getFileName()).head();
        }
    }

    @Nested
    @DisplayName("I-05 A report authorises nothing")
    class AReportAuthorisesNothing {

        @Test
        @DisplayName("AT-05 edi task promote refuses a scout without a recorded captain authorisation")
        void promotionNeedsTheCaptainsAuthorisation() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            home.storing(List.of(WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-4"),
                    WorkerFixtures.queued("survey-doors", TaskKind.SCOUT, "harness", "HRN-5")));
            CallingSession session = primarySessionIn(home, "Ship the lamp survey's plan.");

            assertThatThrownBy(() -> promote(home, "survey-lamps", session))
                    .isInstanceOf(Refusal.class).hasMessageContaining("need the captain's word on survey-lamps in this session");
            assertThatThrownBy(() -> home.captainsWord().record(session, "Ship it all.", TaskReference.parse("survey-lamps"), null, Actor.PRIMARY))
                    .isInstanceOf(Refusal.class).hasMessageContaining("did not type");
            home.captainsWord().record(session, "ship the lamp survey\u2019s   plan.", TaskReference.parse("survey-doors"), null, Actor.PRIMARY);
            assertThatThrownBy(() -> promote(home, "survey-lamps", session)).isInstanceOf(Refusal.class);
            assertThat(home.tasks().inState(TaskState.QUEUED)).extracting(task -> task.intake().kind()).containsOnly(TaskKind.SCOUT);

            home.captainsWord().record(session, "Ship the lamp survey's plan.", TaskReference.parse("survey-lamps"), null, Actor.PRIMARY);
            PromotedScout promoted = promote(home, "survey-lamps", session);

            Task ship = home.tasks().get(promoted.ship().id());
            assertThat(ship.intake().kind()).isEqualTo(TaskKind.SHIP);
            assertThat(ship.intake().workKey()).hasToString("HRN-4");
            assertThat(ship.intake().projects()).containsExactly(new ProjectName("harness"));
            assertThat(ship.notes()).contains("Do the work on survey-lamps.", "on the captain's word: \"Ship the lamp survey's plan.\"");
            assertThat(home.eventTypesOf(new TaskId("survey-lamps"))).containsSubsequence("CaptainSaid", "ScoutPromoted");
            assertThatThrownBy(() -> promote(home, promoted.ship().id().value(), session))
                    .isInstanceOf(Refusal.class).hasMessageContaining("only a scout is promoted");
        }

        @Test
        @DisplayName("AT-05 the captain's choice of the option that authorises promote, on the scout's board or in the console, is his go to promote it")
        void aChoiceOnTheBoardOrInTheConsoleIsTheCaptainsGo() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            Worker scout = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queued("survey-lamps", TaskKind.SCOUT, "harness", "HRN-4"),
                    CategoryId.RESEARCH);
            home.storing(List.of(WorkerFixtures.queued("survey-doors", TaskKind.SCOUT, "harness", "HRN-5")));
            CallingSession session = primarySessionIn(home, "What did the lamp survey find?");
            Board board = armedCarryingTheCloseOutCard(home, scout);
            DecisionNumber doors = home.raiseDecision().raise(TaskReference.parse("survey-doors"), closeOutCard(), Actor.PRIMARY);

            home.receiveBoardFeedback().receive(board.id(), new BoardFeedback.Answered(List.of(new BoardPrompt("7", "choice", "Close out: A",
                    "Close-out decision: option A", "section#decision > form", Map.of("question", "close-out", "answer", "A"), List.of())), false));
            home.answerDecision().answer(new TaskReference.ByDecision(doors), new GivenAnswer("A: ship the door plan", new OptionKey('A'), "",
                    AnswerChannel.CONSOLE), Actor.CAPTAIN);

            assertThat(home.tasks().get(promote(home, "survey-lamps", session).ship().id()).notes())
                    .contains("on the captain's word: \"Close-out decision: option A\"");
            assertThat(home.tasks().get(promote(home, "survey-doors", session).ship().id()).notes())
                    .contains("on the captain's word: \"A: ship the door plan\"");
        }

        private static Board armedCarryingTheCloseOutCard(TestHome home, Worker scout) throws IOException {
            Path board = Files.writeString(Files.createDirectories(home.home().taskDirectory(scout.id().task())).resolve("board.html"),
                    "<!doctype html>\n");
            home.boardHost().holdsTheNextPollOpen();
            return home.armBoard().arm(scout.id(), TaskReference.parse("survey-lamps"), board,
                    new BoardRound(BoardRounds.LINK, 0, List.of(), new BoardCard("close-out", closeOutCard())));
        }

        private static DecisionCard closeOutCard() {
            return new DecisionCard("Ship the survey's plan?", "The survey is done.",
                    List.of(new DecisionOption(new OptionKey('A'), "Ship the plan", CaptainWordAct.PROMOTE), new DecisionOption(new OptionKey('B'), "Close it")),
                    new Recommendation(new OptionKey('A'), "the plan is ready"));
        }

        private static PromotedScout promote(TestHome home, String scout, CallingSession session) {
            return home.promoteScout().promote(TaskReference.parse(scout), null, session, Actor.PRIMARY);
        }
    }

    @Nested
    @DisplayName("I-06 Destructive, irreversible and security-sensitive actions need the captain's word in the current exchange")
    class DestructiveActionsNeedTheCaptainsWord {

        @Test
        @Tag("adapter")
        @DisplayName("AT-06 discarding a worktree or work, deleting an unlanded branch and touching secrets need the captain's word for that act this session")
        void destructiveCommandsNeedTheCaptainsWordForThatActFromThisSession() throws IOException {
            TestHome home = WorkerFixtures.homeWithProjects(directory);
            Path lampCopy = unlandedCopyOf(home, "fit-lamp", "HRN-3");
            Path doorCopy = unlandedCopyOf(home, "fit-door", "HRN-6");
            Path hingeCopy = unlandedCopyOf(home, "fit-hinge", "HRN-7");
            FixtureRepository.at(hingeCopy).commit("HRN-7 Fit the hinge");
            home.git().removeWorktree(home.home().root().resolve("projects/harness"), hingeCopy);
            home.runtime().closeEveryWindow();
            CallingSession earlier = primarySessionIn(home, "D58 A");
            answerOnTask(home, "fit-lamp", Cards.authorising("Discard the work on fit-lamp?", CaptainWordAct.DISCARD_WORK), "D58 A");
            home.clock().advance(Duration.ofHours(1));
            CallingSession current = primarySessionIn(home, "Read the API token to check it.");
            answerOnTask(home, "fit-door", Cards.authorising("Drop fit-door?", CaptainWordAct.DROP), "D60 A");
            answerOnTask(home, "fit-hinge", Cards.authorising("Discard the work on fit-hinge?", CaptainWordAct.DISCARD_WORK), "D61 B");

            assertThatThrownBy(() -> home.discardWork().discard(TaskReference.parse("fit-lamp"), current, Actor.PRIMARY))
                    .isInstanceOf(Refusal.class).hasMessageContaining("To discard work that has not landed, you need the captain's word on fit-lamp");
            assertThatThrownBy(() -> home.dropTask().drop(TaskReference.parse("fit-door"), true, current, Actor.PRIMARY))
                    .isInstanceOf(Refusal.class).hasMessageContaining("on fit-door in this session");
            assertThatThrownBy(() -> home.dropTask().drop(TaskReference.parse("fit-door"), false, current, Actor.PRIMARY))
                    .isInstanceOf(Refusal.class).hasMessageContaining("its work is not landed: door.txt is not committed");
            assertThatThrownBy(() -> home.discardWork().discard(TaskReference.parse("fit-hinge"), current, Actor.PRIMARY))
                    .isInstanceOf(Refusal.class).hasMessageContaining("on fit-hinge in this session");
            assertThat(guardedRead(home, current).verdict()).isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
            assertThat(guardedRead(home, earlier).reason()).contains("touches a secret", "edi captain said");
            assertThat(guardedFileRead(home, earlier).reason()).contains("touches a secret", "edi captain said");
            assertThat(guarded(home, current, "git branch -D feature/HRN-3").reason()).contains("has not landed", "edi captain said");
            assertThat(guarded(home, current, "git -C projects/harness push origin --delete feature/HRN-6").verdict())
                    .isEqualTo(HookAnswer.Verdict.DENY_THE_TOOL);
            assertThat(guarded(home, current, "git branch -D feature/HRN-99")).isEqualTo(HookAnswer.PROCEED);
            assertThat(lampCopy).isDirectory();
            assertThat(doorCopy).isDirectory();

            answerOnTask(home, "fit-lamp", Cards.authorising("Discard the work on fit-lamp?", CaptainWordAct.DISCARD_WORK), "D59 A");
            answerOnTask(home, "fit-door", Cards.authorising("Drop fit-door and its work?", CaptainWordAct.DISCARD_WORK), "D60 A");
            answerOnTask(home, "fit-hinge", Cards.authorising("Discard the work on fit-hinge?", CaptainWordAct.DISCARD_WORK), "D61 A");
            home.captainsWord().record(current, "Read the API token to check it.", null, null, Actor.PRIMARY);

            assertThat(home.discardWork().discard(TaskReference.parse("fit-lamp"), current, Actor.PRIMARY).discarded())
                    .containsExactly("lamp.txt is not committed");
            assertThat(home.dropTask().drop(TaskReference.parse("fit-door"), true, current, Actor.PRIMARY).task().state()).isEqualTo(TaskState.DROPPED);
            assertThat(home.discardWork().discard(TaskReference.parse("fit-hinge"), current, Actor.PRIMARY).discarded())
                    .singleElement().asString().contains("HRN-7 Fit the hinge");
            assertThat(guardedRead(home, current)).isEqualTo(HookAnswer.PROCEED);
            assertThat(guardedFileRead(home, current)).isEqualTo(HookAnswer.PROCEED);
            assertThat(guarded(home, current, "git branch -D feature/HRN-3")).isEqualTo(HookAnswer.PROCEED);
            assertThat(lampCopy).doesNotExist();
            assertThat(doorCopy).doesNotExist();
            assertThat(home.git().commitAt(home.home().root().resolve("projects/harness"), "refs/heads/feature/HRN-7")).isEmpty();
            assertThat(home.eventTypesOf(new TaskId("fit-door"))).endsWith("TaskDropped");
            assertThat(home.eventTypesOf(new TaskId("fit-lamp"))).endsWith("WorkDiscarded");
        }

        private static Path unlandedCopyOf(TestHome home, String task, String key) throws IOException {
            Worker worker = WorkerFixtures.dispatchedShip(home, task, key);
            Path copy = home.home().worktreeOf(new ProjectName("harness"), worker.id().task());
            FixtureRepository.at(copy).write(task.substring("fit-".length()) + ".txt", "brass\n");
            return copy;
        }

        private static void answerOnTask(TestHome home, String task, DecisionCard card, String verbatim) {
            home.raiseDecision().raise(TaskReference.parse(task), card, Actor.PRIMARY);
            OptionKey chosen = new OptionKey(verbatim.charAt(verbatim.length() - 1));
            home.answerDecision().answer(TaskReference.parse(task), new GivenAnswer(verbatim, chosen, "", AnswerChannel.CHAT), Actor.PRIMARY);
        }

        private static HookAnswer guardedRead(TestHome home, CallingSession session) {
            return guarded(home, session, "cat ~/.edi/state/core.token");
        }

        private static HookAnswer guardedFileRead(TestHome home, CallingSession session) {
            return home.guardToolCall().checkForPrimary(new ToolCall("Read", null, home.home().coreTokenFile().toString(), home.home().root()), session);
        }

        private static HookAnswer guarded(TestHome home, CallingSession session, String command) {
            return home.guardToolCall().checkForPrimary(new ToolCall("Bash", command, null, home.home().root()), session);
        }
    }

    private static CallingSession primarySessionIn(TestHome home, String typed) {
        UUID session = UUID.randomUUID();
        home.primaryHooks().sessionStarted(session, null, SessionSource.LAUNCH);
        home.primaryHooks().promptSubmitted(session, typed);
        return new CallingSession(session);
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
