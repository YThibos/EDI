package io.github.ythibos.edi.adapter.forge;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.application.port.Forge;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.DraftProposal;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.MergeRequestAddress;
import io.github.ythibos.edi.domain.delivery.MergeRequestState;
import io.github.ythibos.edi.domain.delivery.MergeRequestView;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.publish.RequestText;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GitLabForge implements Forge {

    private final ForgeCalls calls;
    private final JsonMapper json;

    public GitLabForge(ForgeCalls calls, JsonMapper json) {
        this.calls = calls;
        this.json = json;
    }

    @Override
    public ForgeKind kind() {
        return ForgeKind.GITLAB;
    }

    @Override
    public List<MergeRequestView> mergeRequestsFrom(RegisteredForge forge, BranchName branch) {
        String listed = calls.run(ForgeCommand.GLAB_MR_LIST,
                List.of("-R", forge.repository().url(), "--source-branch", branch.toString(), "--all", "-F", "json"), Map.of(), null);
        return StreamSupport.stream(json.readTree(listed).spliterator(), false).map(request -> viewOf(forge, request)).toList();
    }

    @Override
    public MergeRequestView view(RegisteredForge forge, MergeRequestAddress address) {
        String viewed = calls.run(ForgeCommand.GLAB_MR_VIEW,
                List.of(String.valueOf(address.number()), "-R", forge.repository().url(), "-F", "json"), Map.of(), null);
        return viewOf(forge, json.readTree(viewed));
    }

    @Override
    public MergeRequestView openDraft(RegisteredForge forge, DraftProposal proposal) {
        requireTheBoundAccount(forge);
        calls.run(ForgeCommand.GLAB_MR_CREATE, List.of("-R", forge.repository().url(), "--draft",
                "--source-branch", proposal.branch().toString(), "--target-branch", proposal.base().name(),
                "--title", proposal.title(), "--description", proposal.description(), "--yes"), Map.of(), null);
        return MergeRequestView.openAmong(mergeRequestsFrom(forge, proposal.branch()))
                .orElseThrow(() -> new ProgramFailure("glab opened no merge request from " + proposal.branch() + " on " + forge.repository() + "."));
    }

    @Override
    public RequestText textOf(RegisteredForge forge, MergeRequestAddress address) {
        JsonNode request = json.readTree(calls.run(ForgeCommand.GLAB_MR_VIEW,
                List.of(String.valueOf(address.number()), "-R", forge.repository().url(), "-F", "json"), Map.of(), null));
        return new RequestText(request.path("title").asString(""), request.path("description").asString(""));
    }

    @Override
    public boolean mayChangeWorkflows(RegisteredForge forge) {
        return true;
    }

    @Override
    public Optional<String> credentialHelperFor(RegisteredForge forge) {
        return Optional.empty();
    }

    private void requireTheBoundAccount(RegisteredForge forge) {
        String user = calls.run(ForgeCommand.GLAB_API_USER, List.of("--hostname", forge.repository().host()), Map.of(), null);
        String loggedIn = json.readTree(user).path("username").asString("");
        if (!forge.account().equals(loggedIn)) {
            throw new Refusal("glab is logged in to " + forge.repository().host() + " as " + (loggedIn.isEmpty() ? "nobody" : loggedIn)
                    + ", but " + forge.repository() + " is bound to " + forge.account() + "; log that account in with glab auth login first.");
        }
    }

    private static MergeRequestView viewOf(RegisteredForge forge, JsonNode request) {
        MergeRequestAddress address = new MergeRequestAddress(forge.repository(), request.path("iid").asInt());
        if (!address.url().equalsIgnoreCase(request.path("web_url").asString(""))) {
            throw new ProgramFailure("glab answered with " + request.path("web_url").asString("") + ", which is not " + address + ".");
        }
        MergeRequestState state = stateOf(request.path("state").asString(""));
        return new MergeRequestView(
                address,
                state,
                request.path("draft").asBoolean(request.path("work_in_progress").asBoolean(false)),
                request.path("source_branch").asString(null),
                request.path("target_branch").asString(null),
                request.path("sha").asString(null),
                state == MergeRequestState.MERGED ? landedCommitOf(request) : null,
                state == MergeRequestState.MERGED ? mergedAtOf(address, request) : null);
    }

    private static MergeRequestState stateOf(String word) {
        return switch (word) {
            case "opened", "locked" -> MergeRequestState.OPEN;
            case "merged" -> MergeRequestState.MERGED;
            case "closed" -> MergeRequestState.CLOSED;
            default -> throw new ProgramFailure("glab reported a merge request in the unknown state " + word + ".");
        };
    }

    private static String landedCommitOf(JsonNode request) {
        return Stream.of("merge_commit_sha", "squash_commit_sha", "sha")
                .map(field -> request.path(field).asString(""))
                .filter(commit -> !commit.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static Instant mergedAtOf(MergeRequestAddress address, JsonNode request) {
        String moment = request.path("merged_at").asString("");
        try {
            return Instant.parse(moment);
        } catch (DateTimeParseException unreadable) {
            throw new ProgramFailure("glab shows " + address + " merged without a readable merged_at: '" + moment + "'.");
        }
    }
}
