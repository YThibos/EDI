package io.github.ythibos.edi.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.OutgoingCommit;

class ForgeAddressesTest {

    private static final ForgeRepository GITHUB = ForgeRepository.registered(ForgeKind.GITHUB, null, "example-owner/harness");
    private static final ForgeRepository GITLAB = ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org", "team/tools/harness");

    @Test
    void buildsTheAddressesOfARepositoryAndItsMergeRequests() {
        assertThat(GITHUB.url()).isEqualTo("https://github.com/example-owner/harness");
        assertThat(GITHUB.mergeRequestUrl(7)).isEqualTo("https://github.com/example-owner/harness/pull/7");
        assertThat(GITLAB.mergeRequestUrl(12)).isEqualTo("https://forge.example.org/team/tools/harness/-/merge_requests/12");
    }

    @Test
    void refusesARepositoryWithoutAHostOrAPath() {
        assertThatIllegalArgumentException().isThrownBy(() -> ForgeRepository.registered(ForgeKind.GITLAB, null, "team/harness"))
                .withMessageContaining("names its host");
        for (String path : List.of("harness", "/team/harness", "team/harness/", "team/../harness", "team/.hidden", "team//harness")) {
            assertThatIllegalArgumentException().as(path).isThrownBy(() -> ForgeRepository.registered(ForgeKind.GITHUB, null, path));
        }
    }

    @Test
    void readsAMergeRequestAddressOnlyWhenItRebuildsExactly() {
        assertThat(MergeRequestAddress.parse("https://github.com/example-owner/harness/pull/7", GITHUB)).isEqualTo(new MergeRequestAddress(GITHUB, 7));
        assertThat(MergeRequestAddress.parse("https://forge.example.org/team/tools/harness/-/merge_requests/12", GITLAB).number()).isEqualTo(12);
        for (String url : List.of("https://github.com/example-owner/harness/pull/07", "https://github.com/example-owner/harness/pull/7/",
                "https://github.com/someone-else/harness/pull/7", "https://github.com/example-owner/harness/merge_requests/7",
                "http://github.com/example-owner/harness/pull/7", "https://github.com/example-owner/harness/pull/0")) {
            assertThatIllegalArgumentException().as(url).isThrownBy(() -> MergeRequestAddress.parse(url, GITHUB));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/example-owner/harness.git", "https://github.com/example-owner/harness", "https://GitHub.com/Example-Owner/Harness/",
        "https://someone:a-token@github.com/example-owner/harness.git", "ssh://git@github.com:22/example-owner/harness.git",
        "git@github.com:example-owner/harness.git", "git://github.com/example-owner/harness"})
    void recognisesTheRegisteredRepositoryInEveryRemoteForm(String remote) {
        assertThat(RemoteAddress.of(remote)).hasValueSatisfying(address -> assertThat(GITHUB.isAt(address)).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/srv/git/harness.git", "../harness", "file:///srv/git/harness.git", "", "harness"})
    void findsNoForgeInALocalRemote(String remote) {
        assertThat(RemoteAddress.of(remote)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://forge.example$(touch${IFS}harbour)/team/harness", "https://forge.example;id/team/harness", "git@forge.example`id`:team/harness",
        "ssh://git@forge.example|id:22/team/harness", "https://forge..example/team/harness", "https://-forge.example/team/harness",
        "https://forge-.example/team/harness", "https://[::1]/team/harness", "https://forge example/team/harness"})
    void refusesARemoteWhoseHostIsNoDnsName(String remote) {
        assertThat(RemoteAddress.of(remote)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://forge.example.org:8443/team/harness", "ssh://git@forge.example.org:2222/team/harness.git", "git@forge.example.org:team/harness.git",
        "https://Forge.Example.org/team/harness"})
    void readsTheHostOfARemoteWithOrWithoutAPort(String remote) {
        assertThat(RemoteAddress.of(remote)).hasValueSatisfying(address -> assertThat(address.host()).isEqualToIgnoringCase("forge.example.org"));
    }

    @Test
    void registersOnlyAHostThatIsADnsNameWithAnOptionalPort() {
        for (String host : List.of("forge.example$(id)", "forge.example;id", "forge..example", "forge-.example", "forge.example:", "forge.example:84a3",
                "forge.example:8443:1", "forge.example:70000", "forge.example:0", "x".repeat(64) + ".example")) {
            assertThatIllegalArgumentException().as(host).isThrownBy(() -> ForgeRepository.registered(ForgeKind.GITLAB, host, "team/harness"));
        }
        assertThat(ForgeRepository.registered(ForgeKind.GITLAB, "forge.example.org:8443", "team/harness").url())
                .isEqualTo("https://forge.example.org:8443/team/harness");
        assertThat(ForgeRepository.registered(ForgeKind.GITLAB, "10.0.0.5", "team/harness").host()).isEqualTo("10.0.0.5");
    }

    @Test
    void refusesAPushElsewhereWithoutRepeatingItsCredentials() {
        RegisteredForge forge = new RegisteredForge(GITHUB, "example-owner");

        forge.requirePublicationAt("git@github.com:example-owner/harness.git");
        assertThatThrownBy(() -> forge.requirePublicationAt("https://someone:a-token@github.com/someone-else/harness.git"))
                .isInstanceOf(Refusal.class)
                .hasMessage("This work is published only to https://github.com/example-owner/harness, the repository its project registers,"
                        + " and the push goes to github.com/someone-else/harness.");
        assertThatThrownBy(() -> forge.requirePublicationAt("/srv/git/harness.git")).isInstanceOf(Refusal.class)
                .hasMessageEndingWith("the push goes to a remote outside every forge.");
    }

    @Test
    void bindsAnAccountNameThatIsSafeOnACommandLine() {
        for (String account : List.of("", "two words", "owner;rm", "$(whoami)")) {
            assertThatIllegalArgumentException().as(account).isThrownBy(() -> new RegisteredForge(GITHUB, account));
        }
        assertThat(new RegisteredForge(GITLAB, "first.last-2_x").account()).isEqualTo("first.last-2_x");
    }

    @Test
    void knowsWhenAPushChangesGitHubWorkflows() {
        OutgoingChanges workflows = changesTo(".github/workflows/ci.yml");
        OutgoingChanges code = changesTo("src/Lamp.java");

        assertThat(new RegisteredForge(GITHUB, "example-owner").takesWorkflowChangesIn(workflows)).isTrue();
        assertThat(new RegisteredForge(GITHUB, "example-owner").takesWorkflowChangesIn(code)).isFalse();
        assertThat(new RegisteredForge(GITLAB, "example-owner").takesWorkflowChangesIn(workflows)).isFalse();
    }

    private static OutgoingChanges changesTo(String path) {
        return new OutgoingChanges(List.of(new OutgoingCommit("abc", "A <a@example.org>", "A <a@example.org>", "HRN-3 Change", List.of(path),
                List.of(new AddedLine(path, 1, "x")))));
    }
}
