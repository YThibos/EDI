package io.github.ythibos.edi.adapter.forge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.testing.ForgeStandIns;
import tools.jackson.databind.json.JsonMapper;

class GitHubForgeTest {

    private static final RegisteredForge FORGE = new RegisteredForge(ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness"),
            "example-owner");
    private static final BranchName BRANCH = BranchName.parse("feature/HRN-3");
    private static final String OPEN_DRAFT = """
            {"number":7,"url":"https://github.com/example-owner/harness/pull/7","state":"OPEN","isDraft":true,"headRefName":"feature/HRN-3",
             "baseRefName":"main","headRefOid":"1111111111111111111111111111111111111111","mergeCommit":null,
             "mergedAt":null}""";
    private static final String MERGED_AT = "\"mergedAt\":\"2026-10-21T12:37:04Z\"";

    @TempDir
    private Path directory;

    private ForgeStandIns standIns;
    private GitHubForge forge;

    @BeforeEach
    void installTheStandIns() throws IOException {
        standIns = ForgeStandIns.in(directory.resolve("tools"));
        forge = new GitHubForge(new ForgeCalls(new ExternalPrograms(), standIns.gh(), standIns.glab()), new JsonMapper());
    }

    @Test
    void findsTheOpenPullRequestOfABranchUnderTheBoundAccount() throws IOException {
        standIns.answering("pr-list", "[" + OPEN_DRAFT + "]");

        assertThat(forge.mergeRequestsFrom(FORGE, BRANCH)).containsExactly(new MergeRequestView(new MergeRequestAddress(FORGE.repository(), 7), MergeRequestState.OPEN, true,
                "feature/HRN-3", "main", "1111111111111111111111111111111111111111", null, null));
        assertThat(standIns.calls()).containsExactly(
                "auth token --hostname github.com --user example-owner",
                "pr list --repo github.com/example-owner/harness --head feature/HRN-3 --state all --json "
                        + "number,url,state,isDraft,headRefName,baseRefName,headRefOid,mergeCommit,mergedAt");
        assertThat(standIns.tokens()).containsExactly("none", ForgeStandIns.TOKEN);
    }

    @Test
    void listsNoPullRequestFromABranchWithoutOneAndAClosedOneAsClosed() throws IOException {
        standIns.answering("pr-list", "[]");
        assertThat(forge.mergeRequestsFrom(FORGE, BRANCH)).isEmpty();

        standIns.answering("pr-list", "[" + OPEN_DRAFT.replace("\"OPEN\"", "\"CLOSED\"") + "]");
        assertThat(forge.mergeRequestsFrom(FORGE, BRANCH)).extracting(MergeRequestView::state).containsExactly(MergeRequestState.CLOSED);
    }

    @Test
    void listsEveryPullRequestOfABranchWithAMergeReadAsLandedAndTimed() throws IOException {
        String merged = OPEN_DRAFT.replace("\"OPEN\"", "\"MERGED\"").replace("\"isDraft\":true", "\"isDraft\":false")
                .replace("\"mergeCommit\":null", "\"mergeCommit\":{\"oid\":\"3333\"}").replace("\"mergedAt\":null", MERGED_AT);
        String laterOpen = OPEN_DRAFT.replace("\"number\":7", "\"number\":8").replace("pull/7", "pull/8");

        standIns.answering("pr-list", "[" + merged + "," + laterOpen + "]");
        assertThat(forge.mergeRequestsFrom(FORGE, BRANCH)).extracting(request -> request.address().number()).containsExactly(7, 8);

        standIns.answering("pr-list", "[" + merged + "]");
        assertThat(forge.mergeRequestsFrom(FORGE, BRANCH)).singleElement().satisfies(request -> {
            assertThat(request.isMerged()).isTrue();
            assertThat(request.draft()).isFalse();
            assertThat(request.landedCommit()).isEqualTo("3333");
            assertThat(request.mergedAt()).isEqualTo(Instant.parse("2026-10-21T12:37:04Z"));
        });
    }

    @Test
    void readsTheTitleAndDescriptionOfAPullRequestUnderTheBoundAccount() throws IOException {
        standIns.answering("pr-view", "{\"title\":\"HRN-3 Fit the lamp\",\"body\":\"Fits the lamp.\\nIn the hall.\"}");

        RequestText text = forge.textOf(FORGE, new MergeRequestAddress(FORGE.repository(), 7));

        assertThat(text).isEqualTo(new RequestText("HRN-3 Fit the lamp", "Fits the lamp.\nIn the hall."));
        assertThat(standIns.calls()).endsWith("pr view 7 --repo github.com/example-owner/harness --json title,body");
    }

    @Test
    void readsTheCommitAMergeLanded() throws IOException {
        standIns.answering("pr-view", OPEN_DRAFT.replace("\"OPEN\"", "\"MERGED\"").replace("\"mergeCommit\":null", "\"mergeCommit\":{\"oid\":\"3333\"}")
                .replace("\"mergedAt\":null", MERGED_AT));

        MergeRequestView merged = forge.view(FORGE, new MergeRequestAddress(FORGE.repository(), 7));

        assertThat(merged.isMerged()).isTrue();
        assertThat(merged.landedCommit()).isEqualTo("3333");
    }

    @Test
    void failsOnAMergedPullRequestWithoutAReadableMergeTime() throws IOException {
        String merged = OPEN_DRAFT.replace("\"OPEN\"", "\"MERGED\"").replace("\"mergeCommit\":null", "\"mergeCommit\":{\"oid\":\"3333\"}");
        MergeRequestAddress address = new MergeRequestAddress(FORGE.repository(), 7);

        standIns.answering("pr-view", merged);
        assertThatThrownBy(() -> forge.view(FORGE, address)).isInstanceOf(ProgramFailure.class)
                .hasMessage("gh shows " + address + " merged without a readable mergedAt: ''.");

        standIns.answering("pr-list", "[" + merged.replace("\"mergedAt\":null", "\"mergedAt\":\"yesterday\"") + "]");
        assertThatThrownBy(() -> forge.mergeRequestsFrom(FORGE, BRANCH)).isInstanceOf(ProgramFailure.class)
                .hasMessage("gh shows " + address + " merged without a readable mergedAt: 'yesterday'.");
    }

    @Test
    void refusesAnAnswerForAnotherRepository() throws IOException {
        standIns.answering("pr-view", OPEN_DRAFT.replace("example-owner/harness", "someone-else/harness"));

        assertThatThrownBy(() -> forge.view(FORGE, new MergeRequestAddress(FORGE.repository(), 7)))
                .isInstanceOf(ProgramFailure.class).hasMessageContaining("which is not https://github.com/example-owner/harness/pull/7");
    }

    @Test
    void opensADraftWithItsBodyOnStandardInput() throws IOException {
        standIns.answering("pr-create", "https://github.com/example-owner/harness/pull/7\n").answering("pr-list", "[" + OPEN_DRAFT + "]");

        MergeRequestView opened = forge.openDraft(FORGE, DraftProposal.forWork(BRANCH, new BaseBranch("main"), "Fit the lamp", "The brass lamp."));

        assertThat(opened.draft()).isTrue();
        assertThat(standIns.calls()).contains("pr create --repo github.com/example-owner/harness --draft --base main --head feature/HRN-3"
                + " --title HRN-3 Fit the lamp --body-file -");
        assertThat(standIns.body()).isEqualTo("The brass lamp.");
    }

    @Test
    void knowsWhetherTheBoundAccountMayChangeWorkflows() throws IOException {
        standIns.answering("auth-status", """
                {"hosts":{"github.com":[{"login":"someone-else","scopes":"repo, workflow"},{"login":"example-owner","scopes":"gist, repo"}]}}""");
        assertThat(forge.mayChangeWorkflows(FORGE)).isFalse();

        standIns.answering("auth-status", """
                {"hosts":{"github.com":[{"login":"example-owner","scopes":"gist, read:org, repo, workflow"}]}}""");
        assertThat(forge.mayChangeWorkflows(FORGE)).isTrue();
    }

    @Test
    void bindsGitTransportToTheAccountThroughGh() {
        assertThat(forge.credentialHelperFor(FORGE)).hasValueSatisfying(helper -> assertThat(helper)
                .startsWith("!f() { test \"$1\" = get || exit 0; GH_TOKEN=\"$('" + standIns.gh() + "' auth token --hostname github.com --user example-owner)\"")
                .endsWith("exec '" + standIns.gh() + "' auth git-credential get; }; f"));
    }
}
