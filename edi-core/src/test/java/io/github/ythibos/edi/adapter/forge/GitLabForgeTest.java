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
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.testing.ForgeStandIns;
import tools.jackson.databind.json.JsonMapper;

class GitLabForgeTest {

    private static final RegisteredForge FORGE = new RegisteredForge(ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org", "team/harness"),
            "example-owner");
    private static final BranchName BRANCH = BranchName.parse("feature/HRN-3");
    private static final String REQUEST = """
            {"iid":5,"web_url":"https://forge.example.org/team/harness/-/merge_requests/5","state":"%s","draft":true,
             "source_branch":"feature/HRN-3","target_branch":"main","sha":"2222","merge_commit_sha":%s,"squash_commit_sha":%s,
             "merged_at":%s}""";
    private static final String MERGED_AT = "\"2026-10-21T12:37:04.512Z\"";

    @TempDir
    private Path directory;

    private ForgeStandIns standIns;
    private GitLabForge forge;

    @BeforeEach
    void installTheStandIns() throws IOException {
        standIns = ForgeStandIns.in(directory.resolve("tools"));
        forge = new GitLabForge(new ForgeCalls(new ExternalPrograms(), standIns.gh(), standIns.glab()), new JsonMapper());
    }

    @Test
    void findsTheOpenMergeRequestOfABranchByTheProjectsUrl() throws IOException {
        standIns.answering("mr-list", "[" + REQUEST.formatted("opened", "null", "null", "null") + "]");

        MergeRequestView open = forge.mergeRequestFrom(FORGE, BRANCH).orElseThrow();

        assertThat(open).isEqualTo(new MergeRequestView(new MergeRequestAddress(FORGE.repository(), 5), MergeRequestState.OPEN, true,
                "feature/HRN-3", "main", "2222", null, null));
        assertThat(standIns.calls()).containsExactly("mr list -R https://forge.example.org/team/harness --source-branch feature/HRN-3 --all -F json");
    }

    @Test
    void findsTheMergedRequestOfABranchWhenNoneIsOpenAndPassesOverAClosedOne() throws IOException {
        String closed = REQUEST.formatted("closed", "null", "null", "null").replace("\"iid\":5", "\"iid\":6")
                .replace("merge_requests/5", "merge_requests/6");
        standIns.answering("mr-list", "[" + closed + "," + REQUEST.formatted("merged", "\"4444\"", "null", MERGED_AT) + "]");

        MergeRequestView merged = forge.mergeRequestFrom(FORGE, BRANCH).orElseThrow();

        assertThat(merged.address().number()).isEqualTo(5);
        assertThat(merged.landedCommit()).isEqualTo("4444");
        assertThat(merged.mergedAt()).isEqualTo(Instant.parse("2026-10-21T12:37:04.512Z"));

        standIns.answering("mr-list", "[" + closed + "]");
        assertThat(forge.mergeRequestFrom(FORGE, BRANCH)).isEmpty();
    }

    @Test
    void failsOnAMergedRequestWithoutAReadableMergeTime() throws IOException {
        MergeRequestAddress address = new MergeRequestAddress(FORGE.repository(), 5);

        standIns.answering("mr-view", REQUEST.formatted("merged", "\"4444\"", "null", "null"));
        assertThatThrownBy(() -> forge.view(FORGE, address)).isInstanceOf(ProgramFailure.class)
                .hasMessage("glab shows " + address + " merged without a readable merged_at: ''.");

        standIns.answering("mr-list", "[" + REQUEST.formatted("merged", "\"4444\"", "null", "\"yesterday\"") + "]");
        assertThatThrownBy(() -> forge.mergeRequestFrom(FORGE, BRANCH)).isInstanceOf(ProgramFailure.class)
                .hasMessage("glab shows " + address + " merged without a readable merged_at: 'yesterday'.");
    }

    @Test
    void readsEachStateAndTheCommitAMergeLanded() throws IOException {
        MergeRequestAddress address = new MergeRequestAddress(FORGE.repository(), 5);

        standIns.answering("mr-view", REQUEST.formatted("locked", "null", "null", "null"));
        assertThat(forge.view(FORGE, address).state()).isEqualTo(MergeRequestState.OPEN);
        standIns.answering("mr-view", REQUEST.formatted("closed", "null", "null", "null"));
        assertThat(forge.view(FORGE, address).state()).isEqualTo(MergeRequestState.CLOSED);
        standIns.answering("mr-view", REQUEST.formatted("merged", "\"4444\"", "\"5555\"", MERGED_AT));
        assertThat(forge.view(FORGE, address).landedCommit()).isEqualTo("4444");
        standIns.answering("mr-view", REQUEST.formatted("merged", "null", "\"5555\"", MERGED_AT));
        assertThat(forge.view(FORGE, address).landedCommit()).isEqualTo("5555");
        standIns.answering("mr-view", REQUEST.formatted("merged", "null", "null", MERGED_AT));
        assertThat(forge.view(FORGE, address).landedCommit()).as("a fast-forward merge lands the head itself").isEqualTo("2222");
    }

    @Test
    void opensADraftOnlyAsTheBoundAccount() throws IOException {
        standIns.answering("api-user", "{\"username\":\"someone-else\"}");
        DraftProposal proposal = DraftProposal.forWork(BRANCH, new BaseBranch("main"), "Fit the lamp", "The brass lamp.");

        assertThatThrownBy(() -> forge.openDraft(FORGE, proposal)).isInstanceOf(Refusal.class)
                .hasMessageContaining("glab is logged in to forge.example.org as someone-else, but https://forge.example.org/team/harness is bound to example-owner");
        assertThat(standIns.calls()).noneMatch(call -> call.startsWith("mr create"));

        standIns.answering("api-user", "{\"username\":\"example-owner\"}").answering("mr-list", "[" + REQUEST.formatted("opened", "null", "null", "null") + "]");
        assertThat(forge.openDraft(FORGE, proposal).draft()).isTrue();
        assertThat(standIns.calls()).contains("mr create -R https://forge.example.org/team/harness --draft --source-branch feature/HRN-3"
                + " --target-branch main --title HRN-3 Fit the lamp --description The brass lamp. --yes");
    }

    @Test
    void neitherNeedsAWorkflowScopeNorBindsGitTransport() {
        assertThat(forge.mayChangeWorkflows(FORGE)).isTrue();
        assertThat(forge.credentialHelperFor(FORGE)).isEmpty();
    }
}
