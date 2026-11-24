package io.github.ythibos.edi.application.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.application.dispatch.DispatchRequest;
import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.domain.publish.PublishFinding;
import io.github.ythibos.edi.domain.publish.PublishRule;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.PrePushHook;
import io.github.ythibos.edi.testing.PrePushHook.HookRun;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.TestProcesses;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class InternalVocabularyPushTest {

    private static final String HARNESS_REMOTE = "https://github.com/example-owner/harness.git";
    private static final RevisionRange OUTGOING = new RevisionRange("origin/main..HEAD");
    private static final String STORY = """
            ---
            key: %s
            type: Story
            ---

            # Fit the lamp

            %s
            """;

    @TempDir
    private Path directory;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();
    private TestHome home;

    @BeforeAll
    static void requireGitleaks() {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
    }

    @BeforeEach
    void registerTheProjects() throws IOException {
        home = WorkerFixtures.homeWithProjects(directory);
    }

    @Test
    void refusesAPushWhoseStoryFileQuotesTheCaptainWithItsFileAndLine() throws IOException, InterruptedException {
        FixtureRepository worktree = copyOf("fit-lamp", "harness", "HRN-3")
                .write("docs/stories/HRN-3.md", STORY.formatted("HRN-3", "Fitted on the captain's word."))
                .commit("HRN-3 Describe the lamp story");

        HookRun refused = PrePushHook.pushes(worktree, home.home().root(), "feature/HRN-3", HARNESS_REMOTE, processes);

        assertThat(refused.refused()).isTrue();
        assertThat(refused.output()).contains("docs/stories/HRN-3.md:8 in commit " + shortHead(worktree)
                + ": \"captain\" is internal vocabulary; write \"developer\" instead");
    }

    @Test
    void letsThePushThroughOnceTheStoryFileSaysDeveloper() throws IOException {
        FixtureRepository worktree = copyOf("fit-lamp", "harness", "HRN-3")
                .write("docs/stories/HRN-3.md", STORY.formatted("HRN-3", "Fitted on the developer's word."))
                .commit("HRN-3 Describe the lamp story");

        PublishVerdict verdict = checked(worktree);

        assertThat(verdict.findings()).isEmpty();
        assertThat(verdict.conclusion(OUTGOING))
                .isEqualTo("Clear: no secret, no personal material, no -SNAPSHOT version and no internal vocabulary in origin/main..HEAD");
    }

    @Test
    void refusesACommitMessageAndADecisionRecordThatCarryInternalVocabulary() throws IOException {
        FixtureRepository worktree = copyOf("fit-lamp", "harness", "HRN-3")
                .write("docs/adr/0003-brass-lamps.md", "# Brass lamps\n\nBrass, as Q10 A settled.\n")
                .write("src/Lamp.java", "class Lamp { String commander = \"captain\"; }\n")
                .commit("HRN-3 Fit the lamp the Commander asked for");

        assertThat(checked(worktree).findings()).extracting(PublishFinding::rule, PublishFinding::location).containsExactly(
                tuple(PublishRule.EDI_VOCABULARY, "message of commit " + shortHead(worktree)),
                tuple(PublishRule.EDI_VOCABULARY, "docs/adr/0003-brass-lamps.md:3 in commit " + shortHead(worktree)));
    }

    @Test
    void letsEdisOwnRepositorySpeakItsOwnVocabulary() throws IOException {
        FixtureRepository worktree = copyOf("tune-voice", "EDI", "EDI-5")
                .write("docs/stories/EDI-5.md", STORY.formatted("EDI-5", "Fitted on the captain's word, as D58 A settled."))
                .commit("EDI-5 Address the captain as the commander");

        PublishVerdict verdict = checked(worktree);

        assertThat(verdict.allowsPush()).isTrue();
        assertThat(verdict.conclusion(OUTGOING)).isEqualTo("Clear: no secret, no personal material and no -SNAPSHOT version in origin/main..HEAD");
    }

    private FixtureRepository copyOf(String task, String project, String key) {
        home.storing(List.of(WorkerFixtures.queuedShip(task, project, key)));
        Path worktree = home.dispatchTask().dispatch(TaskReference.parse(task), new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null),
                CallingSession.UNNAMED, Actor.PRIMARY).launched().worktree();
        return FixtureRepository.at(worktree);
    }

    private PublishVerdict checked(FixtureRepository worktree) {
        return PublishGuard.publishCheck(home.home().root()).check(worktree.root(), OUTGOING);
    }

    private static String shortHead(FixtureRepository worktree) throws IOException {
        return worktree.head().substring(0, 7);
    }
}
