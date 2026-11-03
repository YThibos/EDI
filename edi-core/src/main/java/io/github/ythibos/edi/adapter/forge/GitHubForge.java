package io.github.ythibos.edi.adapter.forge;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.application.port.Forge;
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
public class GitHubForge implements Forge {

    private static final String FIELDS = "number,url,state,isDraft,headRefName,baseRefName,headRefOid,mergeCommit,mergedAt";
    private static final String TEXT_FIELDS = "title,body";
    private static final String WORKFLOW_SCOPE = "workflow";

    private final ForgeCalls calls;
    private final JsonMapper json;

    public GitHubForge(ForgeCalls calls, JsonMapper json) {
        this.calls = calls;
        this.json = json;
    }

    @Override
    public ForgeKind kind() {
        return ForgeKind.GITHUB;
    }

    @Override
    public List<MergeRequestView> mergeRequestsFrom(RegisteredForge forge, BranchName branch) {
        String listed = asTheBoundAccount(forge, ForgeCommand.GH_PR_LIST,
                List.of("--repo", repositoryOf(forge), "--head", branch.toString(), "--state", "all", "--json", FIELDS), null);
        return StreamSupport.stream(json.readTree(listed).spliterator(), false).map(pull -> viewOf(forge, pull)).toList();
    }

    @Override
    public MergeRequestView view(RegisteredForge forge, MergeRequestAddress address) {
        String viewed = asTheBoundAccount(forge, ForgeCommand.GH_PR_VIEW,
                List.of(String.valueOf(address.number()), "--repo", repositoryOf(forge), "--json", FIELDS), null);
        return viewOf(forge, json.readTree(viewed));
    }

    @Override
    public MergeRequestView openDraft(RegisteredForge forge, DraftProposal proposal) {
        asTheBoundAccount(forge, ForgeCommand.GH_PR_CREATE, List.of("--repo", repositoryOf(forge), "--draft",
                "--base", proposal.base().name(), "--head", proposal.branch().toString(), "--title", proposal.title(), "--body-file", "-"),
                proposal.description());
        return MergeRequestView.openAmong(mergeRequestsFrom(forge, proposal.branch()))
                .orElseThrow(() -> new ProgramFailure("gh opened no pull request from " + proposal.branch() + " on " + forge.repository() + "."));
    }

    @Override
    public RequestText textOf(RegisteredForge forge, MergeRequestAddress address) {
        JsonNode pull = json.readTree(asTheBoundAccount(forge, ForgeCommand.GH_PR_VIEW,
                List.of(String.valueOf(address.number()), "--repo", repositoryOf(forge), "--json", TEXT_FIELDS), null));
        return new RequestText(pull.path("title").asString(""), pull.path("body").asString(""));
    }

    @Override
    public boolean mayChangeWorkflows(RegisteredForge forge) {
        String status = calls.run(ForgeCommand.GH_AUTH_STATUS, List.of("--hostname", forge.repository().host(), "--json", "hosts"), Map.of(), null);
        return StreamSupport.stream(json.readTree(status).path("hosts").path(forge.repository().host()).spliterator(), false)
                .filter(account -> forge.account().equals(account.path("login").asString("")))
                .anyMatch(account -> List.of(account.path("scopes").asString("").split(",\\s*")).contains(WORKFLOW_SCOPE));
    }

    @Override
    public Optional<String> credentialHelperFor(RegisteredForge forge) {
        return Optional.of("!f() { test \"$1\" = get || exit 0; GH_TOKEN=\"$(" + calls.commandLineOf(ForgeCommand.GH_AUTH_TOKEN) + " --hostname "
                + quoted(forge.repository().host()) + " --user " + quoted(forge.account()) + ")\" exec "
                + calls.commandLineOf(ForgeCommand.GH_AUTH_GIT_CREDENTIAL) + " get; }; f");
    }

    private String asTheBoundAccount(RegisteredForge forge, ForgeCommand command, List<String> arguments, String input) {
        return calls.run(command, arguments, Map.of("GH_TOKEN", tokenOf(forge), "GH_PROMPT_DISABLED", "1"), input);
    }

    private String tokenOf(RegisteredForge forge) {
        String token = calls.run(ForgeCommand.GH_AUTH_TOKEN, List.of("--hostname", forge.repository().host(), "--user", forge.account()), Map.of(), null)
                .strip();
        if (token.isEmpty()) {
            throw new ProgramFailure("gh holds no token for " + forge.account() + " on " + forge.repository().host() + "; log the account in with gh auth login.");
        }
        return token;
    }

    private static String repositoryOf(RegisteredForge forge) {
        return forge.repository().host() + "/" + forge.repository().path();
    }

    private static MergeRequestView viewOf(RegisteredForge forge, JsonNode pull) {
        MergeRequestAddress address = new MergeRequestAddress(forge.repository(), pull.path("number").asInt());
        if (!address.url().equalsIgnoreCase(pull.path("url").asString(""))) {
            throw new ProgramFailure("gh answered with " + pull.path("url").asString("") + ", which is not " + address + ".");
        }
        MergeRequestState state = stateOf(pull.path("state").asString(""));
        return new MergeRequestView(
                address,
                state,
                pull.path("isDraft").asBoolean(false),
                pull.path("headRefName").asString(null),
                pull.path("baseRefName").asString(null),
                pull.path("headRefOid").asString(null),
                pull.path("mergeCommit").path("oid").asString(null),
                state == MergeRequestState.MERGED ? mergedAtOf(address, pull) : null);
    }

    private static MergeRequestState stateOf(String word) {
        return switch (word) {
            case "OPEN" -> MergeRequestState.OPEN;
            case "MERGED" -> MergeRequestState.MERGED;
            case "CLOSED" -> MergeRequestState.CLOSED;
            default -> throw new ProgramFailure("gh reported a pull request in the unknown state " + word + ".");
        };
    }

    private static Instant mergedAtOf(MergeRequestAddress address, JsonNode pull) {
        String moment = pull.path("mergedAt").asString("");
        try {
            return Instant.parse(moment);
        } catch (DateTimeParseException unreadable) {
            throw new ProgramFailure("gh shows " + address + " merged without a readable mergedAt: '" + moment + "'.");
        }
    }

    private static String quoted(String word) {
        return "'" + word + "'";
    }
}
