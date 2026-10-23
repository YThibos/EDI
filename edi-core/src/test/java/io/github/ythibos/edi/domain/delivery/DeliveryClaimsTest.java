package io.github.ythibos.edi.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.project.WorkKey;
import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;

class DeliveryClaimsTest {

    private static final ForgeRepository REPOSITORY = ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness");
    private static final MergeRequestAddress ADDRESS = new MergeRequestAddress(REPOSITORY, 7);
    private static final BranchName BRANCH = BranchName.parse("feature/HRN-3");
    private static final BaseBranch MAIN = new BaseBranch("main");
    private static final String HEAD = "1111111111111111111111111111111111111111";
    private static final Instant MERGED_AT = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void findsNoGapInARequestOfTheBranchAtItsHeadWhetherADraftReadyForReviewOrMerged() {
        assertThat(view(MergeRequestState.OPEN, true, "feature/HRN-3", "main", HEAD).gapsAsTheDeliveryOf(BRANCH, MAIN, HEAD)).isEmpty();
        assertThat(view(MergeRequestState.OPEN, false, "feature/HRN-3", "main", HEAD).gapsAsTheDeliveryOf(BRANCH, MAIN, HEAD)).isEmpty();
        assertThat(new MergeRequestView(ADDRESS, MergeRequestState.MERGED, false, "feature/HRN-3", "main", HEAD, "3333", MERGED_AT)
                .gapsAsTheDeliveryOf(BRANCH, MAIN, HEAD)).isEmpty();
    }

    @Test
    void namesEveryGapBetweenAMergeRequestAndTheDeliveryItShouldBe() {
        MergeRequestView wrong = new MergeRequestView(ADDRESS, MergeRequestState.CLOSED, false, "feature/HRN-4", "develop", "2222222222222", null, null);

        assertThat(wrong.gapsAsTheDeliveryOf(BRANCH, MAIN, HEAD)).containsExactly(
                ADDRESS + " is closed without a merge",
                ADDRESS + " comes from feature/HRN-4, not from feature/HRN-3",
                ADDRESS + " targets develop, not main",
                ADDRESS + " shows head 222222222222, but the isolated copy is at 111111111111; push the copy's head first");
    }

    @Test
    void takesTheOpenRequestOfABranchAsItsDeliveryAndOtherwiseItsLatestMerge() {
        MergeRequestView closed = new MergeRequestView(new MergeRequestAddress(REPOSITORY, 9), MergeRequestState.CLOSED, true, "feature/HRN-3",
                "main", HEAD, null, null);
        MergeRequestView open = view(MergeRequestState.OPEN, false, "feature/HRN-3", "main", HEAD);
        MergeRequestView merged = new MergeRequestView(new MergeRequestAddress(REPOSITORY, 8), MergeRequestState.MERGED, false, "feature/HRN-3",
                "main", HEAD, "3333", MERGED_AT);

        assertThat(MergeRequestView.deliveryAmong(List.of(closed, merged, open), request -> true)).contains(open);
        assertThat(MergeRequestView.deliveryAmong(List.of(closed, merged), request -> true)).contains(merged);
        assertThat(MergeRequestView.deliveryAmong(List.of(closed), request -> true)).isEmpty();
    }

    @Test
    void takesTheLatestOwnedMergeWhateverOrderTheForgeListsThemIn() {
        MergeRequestView earlier = new MergeRequestView(new MergeRequestAddress(REPOSITORY, 4), MergeRequestState.MERGED, false, "feature/HRN-3",
                "main", HEAD, "2222", MERGED_AT.minusSeconds(3600));
        MergeRequestView own = new MergeRequestView(new MergeRequestAddress(REPOSITORY, 8), MergeRequestState.MERGED, false, "feature/HRN-3",
                "main", HEAD, "3333", MERGED_AT);
        MergeRequestView later = new MergeRequestView(new MergeRequestAddress(REPOSITORY, 9), MergeRequestState.MERGED, false, "feature/HRN-3",
                "main", HEAD, "4444", MERGED_AT.plusSeconds(3600));

        assertThat(MergeRequestView.deliveryAmong(List.of(earlier, own), request -> request.mergedSince(MERGED_AT))).contains(own);
        assertThat(MergeRequestView.deliveryAmong(List.of(earlier, own, later), request -> true)).contains(later);
        assertThat(MergeRequestView.deliveryAmong(List.of(earlier, own, later), request -> request != later)).contains(own);
        assertThat(MergeRequestView.deliveryAmong(List.of(earlier), request -> request.mergedSince(MERGED_AT))).isEmpty();
    }

    @Test
    void needsTheLandedCommitOfAMerge() {
        assertThatIllegalArgumentException().isThrownBy(() -> view(MergeRequestState.MERGED, true, "feature/HRN-3", "main", HEAD))
                .withMessageContaining("names the commit it landed");
    }

    @Test
    void needsTheMomentOfAMerge() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MergeRequestView(ADDRESS, MergeRequestState.MERGED, false, "feature/HRN-3", "main",
                HEAD, "3333", null)).withMessageContaining("names when it was merged");
    }

    @Test
    void tellsAMergeSinceAMomentFromAnEarlierOne() {
        MergeRequestView merged = new MergeRequestView(ADDRESS, MergeRequestState.MERGED, false, "feature/HRN-3", "main", HEAD, "3333", MERGED_AT);

        assertThat(merged.mergedSince(MERGED_AT.minusSeconds(60))).isTrue();
        assertThat(merged.mergedSince(MERGED_AT)).isTrue();
        assertThat(merged.mergedSince(MERGED_AT.plusSeconds(1))).isFalse();
        assertThat(view(MergeRequestState.OPEN, false, "feature/HRN-3", "main", HEAD).mergedSince(MERGED_AT)).isFalse();
    }

    @Test
    void recordsADeliveryAndFollowsItsState() {
        Instant recorded = Instant.parse("2026-10-16T09:00:00Z");
        Delivery delivery = Delivery.of(new TaskId("fit-lamp"), view(MergeRequestState.OPEN, true, "feature/HRN-3", "main", HEAD), recorded);
        MergeRequestView merged = new MergeRequestView(ADDRESS, MergeRequestState.MERGED, true, "feature/HRN-3", "main", HEAD, "3333", MERGED_AT);

        Delivery observed = delivery.observed(merged, recorded.plusSeconds(600));

        assertThat(delivery.state()).isEqualTo(MergeRequestState.OPEN);
        assertThat(delivery.changesWith(merged)).isTrue();
        assertThat(observed.state()).isEqualTo(MergeRequestState.MERGED);
        assertThat(observed.landedCommit()).isEqualTo("3333");
        assertThat(observed.recordedAt()).isEqualTo(recorded);
        assertThatIllegalArgumentException().isThrownBy(() -> delivery.observed(new MergeRequestView(new MergeRequestAddress(REPOSITORY, 8),
                MergeRequestState.OPEN, true, "feature/HRN-3", "main", HEAD, null, null), recorded));
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
    void composesThePipelineIntentFromTheCaptainsIntentTheSpecAndTheKeyedCommitsWithTheCraftRulesVerbatim() {
        WorkKey key = WorkKey.parse("HRN-3");
        String commits = "## Commits\n\nThe pipeline starts every commit subject it writes, and the pull request's title, with the work key `HRN-3`,"
                + " so a fix summary or a title it asks for is one short line that leaves the key out.";
        assertThat(PipelineIntent.of(key, "Fit the lamp.\nAcceptance: it lights.\n", "\nThe switch also dims it.\n", "  Craft rules:\n- Short methods.\n").text())
                .isEqualTo("## Captain's intent\n\nFit the lamp.\nAcceptance: it lights.\n\n## Spec\n\nThe switch also dims it.\n\n" + commits
                        + "\n\nCraft rules:\n- Short methods.");
        assertThat(PipelineIntent.of(key, "Fit the lamp.", "The switch also dims it.", null).text())
                .isEqualTo("## Captain's intent\n\nFit the lamp.\n\n## Spec\n\nThe switch also dims it.\n\n" + commits);
        assertThatIllegalArgumentException().isThrownBy(() -> PipelineIntent.of(key, "  ", "The switch also dims it.", null));
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
        return new MergeRequestView(ADDRESS, state, draft, source, target, head, null, null);
    }
}
