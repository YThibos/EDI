package io.github.ythibos.edi.application.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.publish.PublishFinding;
import io.github.ythibos.edi.domain.publish.PublishRule;
import io.github.ythibos.edi.domain.publish.PublishVerdict;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.SyntheticSecrets;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class PublishCheckTest {

    private static final String DENIED_HOST = "git.acme-corp.example";
    private static final String DENY_LIST = """
            # synthetic personal deny-list
            jdoe-at-acme

            acme-corp.example
            """;

    @TempDir
    private Path workspace;

    private FixtureRepository repository;
    private Path elsewhereHome;

    @BeforeAll
    static void requireGitleaks() {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
    }

    @BeforeEach
    void createRepositoryAndHome() throws IOException {
        repository = FixtureRepository.initialise(Files.createDirectory(workspace.resolve("project")))
                .write("README.md", "A project.\n")
                .write(".gitignore", "personal/\n.edi/\n")
                .commit("Start the project");
        elsewhereHome = Files.createDirectory(workspace.resolve("edi-home"));
        Files.createDirectories(elsewhereHome.resolve("config"));
        Files.writeString(elsewhereHome.resolve("config/publish-denylist"), DENY_LIST);
    }

    @Test
    void refusesOutgoingSecretsAndNamesWhereTheyAreWithoutTheirValue() throws IOException {
        String accessKey = SyntheticSecrets.awsAccessKeyId();
        String token = SyntheticSecrets.githubToken();
        repository.branch("secrets")
                .write("config/app.properties", "cloud.key=" + accessKey + "\nforge.token=" + token + "\n")
                .commit("Add service settings");

        PublishVerdict verdict = check("main..secrets", elsewhereHome);

        assertThat(verdict.allowsPush()).isFalse();
        assertThat(verdict.findings())
                .extracting(PublishFinding::rule, PublishFinding::location)
                .containsExactlyInAnyOrder(
                        tuple(PublishRule.SECRET, "config/app.properties:1 in commit " + shortHead()),
                        tuple(PublishRule.SECRET, "config/app.properties:2 in commit " + shortHead()));
        assertThat(verdict.findings().toString()).doesNotContain(accessKey).doesNotContain(token);
    }

    @Test
    void refusesDeniedTermsInContentMessagesAndIdentitiesWithoutNamingTheTerm() throws IOException {
        repository.branch("denied")
                .write("docs/mirror.md", "Clone from https://" + DENIED_HOST + "/team/project\n")
                .commitAs("J Doe <j.doe@acme-corp.example>", "Point at the ACME-CORP.EXAMPLE mirror");

        PublishVerdict verdict = check("main..denied", elsewhereHome);

        assertThat(verdict.allowsPush()).isFalse();
        assertThat(verdict.findings())
                .extracting(PublishFinding::rule, PublishFinding::location, PublishFinding::reason)
                .containsExactlyInAnyOrder(
                        tuple(PublishRule.DENIED_TERM, "author of commit " + shortHead(), "deny-list entry 4"),
                        tuple(PublishRule.DENIED_TERM, "committer of commit " + shortHead(), "deny-list entry 4"),
                        tuple(PublishRule.DENIED_TERM, "message of commit " + shortHead(), "deny-list entry 4"),
                        tuple(PublishRule.DENIED_TERM, "docs/mirror.md:1 in commit " + shortHead(), "deny-list entry 4"));
        assertThat(verdict.findings().toString()).doesNotContainIgnoringCase("acme-corp");
    }

    @Test
    void refusesThePersonalLayerAndAForceAddedEdiHome() throws IOException {
        Path homeInsideRepository = repository.root().resolve(".edi");
        repository.branch("personal")
                .write("personal/anchor.local.md", "Personal addendum.\n")
                .write(".edi/config/personal.yaml", "address: Commander\n")
                .forceAdd("personal/anchor.local.md")
                .forceAdd(".edi/config/personal.yaml")
                .commit("Share my settings");

        PublishVerdict verdict = check("main..personal", homeInsideRepository);

        assertThat(verdict.allowsPush()).isFalse();
        assertThat(verdict.findings())
                .extracting(PublishFinding::rule, PublishFinding::location, PublishFinding::reason)
                .containsExactlyInAnyOrder(
                        tuple(PublishRule.PERSONAL_PATH, "personal/anchor.local.md in commit " + shortHead(), "inside the personal layer"),
                        tuple(PublishRule.PERSONAL_PATH, ".edi/config/personal.yaml in commit " + shortHead(), "inside the EDI home"));
    }

    @Test
    void allowsACleanBranch() throws IOException {
        repository.branch("clean")
                .write("src/greeting.txt", "Hello.\n")
                .commit("Greet the reader");

        PublishVerdict verdict = check("main..clean", elsewhereHome);

        assertThat(verdict.allowsPush()).isTrue();
        assertThat(verdict.findings()).isEmpty();
    }

    private PublishVerdict check(String outgoing, Path ediHome) {
        ExternalPrograms programs = new ExternalPrograms();
        PublishCheck publishCheck = new PublishCheck(new EdiHome(ediHome), new Git(programs), new Gitleaks(programs, new JsonMapper()));
        return publishCheck.check(repository.root(), new RevisionRange(outgoing));
    }

    private String shortHead() throws IOException {
        return repository.head().substring(0, 7);
    }
}
