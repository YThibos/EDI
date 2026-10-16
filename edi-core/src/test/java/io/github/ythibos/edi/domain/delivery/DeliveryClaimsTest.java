package io.github.ythibos.edi.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;

class DeliveryClaimsTest {

    private static final ForgeRepository REPOSITORY = ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness");
    private static final MergeRequestAddress ADDRESS = new MergeRequestAddress(REPOSITORY, 7);
    private static final BranchName BRANCH = BranchName.parse("feature/HRN-3");
    private static final BaseBranch MAIN = new BaseBranch("main");
    private static final String HEAD = "1111111111111111111111111111111111111111";

    @Test
    void findsNoGapInAnOpenDraftOfTheBranchAtItsHead() {
        assertThat(view(MergeRequestState.OPEN, true, "feature/HRN-3", "main", HEAD).gapsAsTheDeliveryOf(BRANCH, MAIN, HEAD)).isEmpty();
    }

    @Test
    void namesEveryGapBetweenAMergeRequestAndTheDeliveryItShouldBe() {
        MergeRequestView wrong = new MergeRequestView(ADDRESS, MergeRequestState.CLOSED, false, "feature/HRN-4", "develop", "2222222222222", null);

        assertThat(wrong.gapsAsTheDeliveryOf(BRANCH, MAIN, HEAD)).containsExactly(
                ADDRESS + " is closed, not open",
                ADDRESS + " is not a draft; every merge request EDI delivers stays a draft until the captain merges it",
                ADDRESS + " comes from feature/HRN-4, not from feature/HRN-3",
                ADDRESS + " targets develop, not main",
                ADDRESS + " shows head 222222222222, but the isolated copy is at 111111111111; push the copy's head first");
    }

    @Test
    void needsTheLandedCommitOfAMerge() {
        assertThatIllegalArgumentException().isThrownBy(() -> view(MergeRequestState.MERGED, true, "feature/HRN-3", "main", HEAD))
                .withMessageContaining("names the commit it landed");
    }

    @Test
    void recordsADeliveryAndFollowsItsState() {
        Instant recorded = Instant.parse("2026-10-16T09:00:00Z");
        Delivery delivery = Delivery.of(new TaskId("fit-lamp"), view(MergeRequestState.OPEN, true, "feature/HRN-3", "main", HEAD), recorded);
        MergeRequestView merged = new MergeRequestView(ADDRESS, MergeRequestState.MERGED, true, "feature/HRN-3", "main", HEAD, "3333");

        Delivery observed = delivery.observed(merged, recorded.plusSeconds(600));

        assertThat(delivery.state()).isEqualTo(MergeRequestState.OPEN);
        assertThat(delivery.changesWith(merged)).isTrue();
        assertThat(observed.state()).isEqualTo(MergeRequestState.MERGED);
        assertThat(observed.landedCommit()).isEqualTo("3333");
        assertThat(observed.recordedAt()).isEqualTo(recorded);
        assertThatIllegalArgumentException().isThrownBy(() -> delivery.observed(new MergeRequestView(new MergeRequestAddress(REPOSITORY, 8),
                MergeRequestState.OPEN, true, "feature/HRN-3", "main", HEAD, null), recorded));
    }

    @Test
    void findsTheSnapshotVersionsABranchAddsToItsBuildFiles() {
        List<AddedLine> added = List.of(
                new AddedLine("pom.xml", 6, "      <version>1.2.0-SNAPSHOT</version>"),
                new AddedLine("module/pom.xml", 2, "  <version>2.0.0-snapshot</version>"),
                new AddedLine("pom.xml", 9, "      <url>https://repository.example.org/maven-snapshots</url>"),
                new AddedLine("docs/release.md", 3, "Never push 1.0.0-SNAPSHOT."),
                new AddedLine("notpom.xml", 1, "1.0-SNAPSHOT"));

        List<AddedLine> snapshots = SnapshotVersions.addedIn(added);

        assertThat(snapshots).extracting(line -> line.path() + ":" + line.number()).containsExactly("pom.xml:6", "module/pom.xml:2");
        assertThat(SnapshotVersions.refusalOf(snapshots))
                .isEqualTo("push-bound work carries no -SNAPSHOT version, and the branch adds one at pom.xml:6, module/pom.xml:2");
    }

    @Test
    void proposesADraftTitledByItsKeyWithAPlainBody() {
        DraftProposal proposal = DraftProposal.forWork(BRANCH, MAIN, "  Fit the lamp ", "The brass one.\n");

        assertThat(proposal.title()).isEqualTo("HRN-3 Fit the lamp");
        assertThat(proposal.description()).isEqualTo("The brass one.");
        for (String footer : List.of("Co-authored-by: Claude <noreply@example.org>", "🤖 Generated with Claude Code")) {
            assertThatIllegalArgumentException().as(footer).isThrownBy(() -> DraftProposal.forWork(BRANCH, MAIN, "Fit the lamp", "Done.\n\n" + footer))
                    .withMessageContaining("names no agent or tool");
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new DraftProposal(BRANCH, MAIN, "Fit the lamp", ""))
                .withMessageContaining("starts with its work key HRN-3");
        assertThat(DraftProposal.forWork(BRANCH, MAIN, "Fit the lamp", "Files generated with protoc stay as they are.").description())
                .startsWith("Files generated");
    }

    @Test
    void composesThePipelineIntentWithTheCraftRulesVerbatim() {
        assertThat(PipelineIntent.of("Fit the lamp.\nAcceptance: it lights.\n", "  Craft rules:\n- Short methods.\n").text())
                .isEqualTo("Fit the lamp.\nAcceptance: it lights.\n\nCraft rules:\n- Short methods.");
        assertThat(PipelineIntent.of("Fit the lamp.", null).text()).isEqualTo("Fit the lamp.");
        assertThatIllegalArgumentException().isThrownBy(() -> PipelineIntent.of("  ", null));
    }

    @Test
    void countsEveryRunStatusButAFinishedOneAsRunning() {
        for (String finished : List.of("completed", "failed", "cancelled", "aborted")) {
            assertThat(new PipelineRun("01RUN", finished, null, HEAD, null).isRunning()).as(finished).isFalse();
        }
        for (String running : List.of("pending", "running", "fixing", "awaiting-review")) {
            assertThat(new PipelineRun("01RUN", running, "ci", HEAD, "").isRunning()).as(running).isTrue();
        }
        assertThat(new PipelineRun("01RUN", "running", " ", HEAD, "").activeStep()).isNull();
    }

    @Test
    void holdsBackTheDestructivePartsOfALocalChange() {
        LocalChange change = new LocalChange(List.of(
                ChangedPath.of("src/Lamp.java", PathChange.MODIFIED),
                ChangedPath.of("src/Horn.java", PathChange.ADDED),
                ChangedPath.of("src/Bell.java", PathChange.DELETED),
                ChangedPath.renamed("docs/old.md", "docs/new.md"),
                ChangedPath.of("run.sh", PathChange.MODE_CHANGED)), "patch");

        assertThat(change.heldForTheCaptain()).extracting(ChangedPath::toString)
                .containsExactly("deleted src/Bell.java", "renamed docs/old.md to docs/new.md", "mode changed run.sh");
        assertThat(change.files()).containsExactly("src/Lamp.java", "src/Horn.java", "src/Bell.java", "docs/old.md", "docs/new.md", "run.sh");
        assertThatIllegalArgumentException().isThrownBy(() -> new ChangedPath("a", PathChange.RENAMED, null));
    }

    @Test
    void triesTheCleanUpAfterARefreshOnlyOnceItIsVerified() {
        Instant asked = Instant.parse("2026-10-16T09:00:00Z");
        CloneRefresh refresh = CloneRefresh.awaiting(new TaskId("fit-lamp"), new ProjectName("harness"), MAIN,
                "3333", asked);

        assertThat(refresh.isPending()).isTrue();
        assertThat(refresh.baseRef()).isEqualTo("origin/main");
        assertThatIllegalArgumentException().isThrownBy(() -> refresh.cleanUpTried(asked));
        assertThat(refresh.verified(asked).cleanUpTried(asked).cleanUpTriedAt()).isEqualTo(asked);
    }

    private static MergeRequestView view(MergeRequestState state, boolean draft, String source, String target, String head) {
        return new MergeRequestView(ADDRESS, state, draft, source, target, head, null);
    }
}
