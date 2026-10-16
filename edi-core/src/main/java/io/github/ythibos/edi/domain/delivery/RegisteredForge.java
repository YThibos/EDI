package io.github.ythibos.edi.domain.delivery;

import java.util.Objects;

import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;

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

    public boolean takesWorkflowChangesIn(OutgoingChanges changes) {
        return kind() == ForgeKind.GITHUB && changes.touchAPathUnder(GITHUB_WORKFLOWS);
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
        return new Refusal("EDI publishes this work only to " + repository + ", the repository its project registers, and the push goes to "
                + target + ".");
    }
}
