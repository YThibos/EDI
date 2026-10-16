package io.github.ythibos.edi.invariant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.brief.WorkerBriefs;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.inbox.InboxMessage;
import io.github.ythibos.edi.domain.primary.Standing;
import io.github.ythibos.edi.domain.status.StatusState;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.BriefFixtures;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import io.github.ythibos.edi.web.access.Caller;
import io.github.ythibos.edi.web.access.NotPermitted;

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
            assertThat(home.recordStatus().report(worker.id(), StatusState.DONE, null, null, "The lamp is fitted.").lastStatus().report().text())
                    .isEqualTo("The lamp is fitted.");
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

        @Test
        @DisplayName("AT-03a no adapter method or HTTP call names merge, auto-merge or merge_when_pipeline_succeeds")
        void noAdapterMethodOrHttpCallNamesAMerge() {
            Pending.until(Slice.S5);
        }

        @Test
        @DisplayName("AT-03b the forge command allow-lists hold no merge command and no push to a base branch")
        void theForgeAllowListsHoldNoMerge() {
            Pending.until(Slice.S5);
        }
    }

    @Nested
    @DisplayName("I-04 Drafts only")
    class DraftsOnly {

        @Test
        @DisplayName("AT-04 delivery refuses a pipeline outcome whose merge request is not a draft and records a failure")
        void deliveryRefusesANonDraft() {
            Pending.until(Slice.S5);
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
