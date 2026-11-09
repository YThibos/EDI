package io.github.ythibos.edi.domain.delivery;

import java.util.List;
import java.util.Objects;

import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.rule.Rule;

public record RegisteredForge(ForgeRepository repository, String account) {

    private static final String GITHUB_WORKFLOWS = ".github/workflows/";
    private static final String ACCOUNT_SEPARATORS = "._-";

    public RegisteredForge {
        Objects.requireNonNull(repository, "A registered forge names its repository.");
        if (!isAccountName(account)) {
            throw new IllegalArgumentException(repository + " is registered with the account every forge call for it runs under,"
                    + " a name of letters, digits, dots, dashes and underscores: " + account);
        }
    }

    public ForgeKind kind() {
        return repository.kind();
    }

    public List<String> identifyingNames() {
        return List.of(repository.host(), account);
    }

    public boolean takesWorkflowChangesIn(OutgoingChanges changes) {
        return kind() == ForgeKind.GITHUB && changes.touchAPathUnder(GITHUB_WORKFLOWS);
    }

    public boolean isServedAt(String remoteUrl) {
        return RemoteAddress.of(remoteUrl).filter(repository::isAt).isPresent();
    }

    public void requirePublicationAt(String remoteUrl) {
        RemoteAddress remote = RemoteAddress.of(remoteUrl).orElseThrow(() -> unregistered("a remote outside every forge"));
        if (!repository.isAt(remote)) {
            throw unregistered(remote.toString());
        }
    }

    private static boolean isAccountName(String account) {
        return account != null
                && !account.isEmpty()
                && account.chars().allMatch(character -> AsciiCharacters.isLetterOrDigit(character) || ACCOUNT_SEPARATORS.indexOf(character) >= 0);
    }

    private Refusal unregistered(String target) {
        return new Refusal(Rule.REGISTERED_FORGE, "This work is published only to " + repository
                + ", the repository its project registers, and the push goes to "
                + target + ".");
    }
}
