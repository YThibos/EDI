package io.github.ythibos.edi.application.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.fork.ForkProject;
import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.adapter.registry.NewProject;
import io.github.ythibos.edi.adapter.registry.ProjectRegistry;
import io.github.ythibos.edi.domain.delivery.ForgeKind;
import io.github.ythibos.edi.domain.delivery.ForgeRepository;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.delivery.RemoteAddress;
import io.github.ythibos.edi.domain.project.DeliveryMode;
import io.github.ythibos.edi.domain.project.ProjectKey;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.tracker.TrackerKind;

@Tag("adapter")
class RegistryImportTest {

    private static final String REGISTERED = """
            projects:
              - name: lighthouse
                forge: { kind: gitlab, host: forge.example.org, path: team/lighthouse, account: keeper }
                tracker: { kind: edi, projectKey: LHT }
            """;

    @TempDir
    private Path home;

    @Test
    void addsTheOldProjectsEdiLacksWithWhatTheirLinesAndClonesTell() throws IOException {
        MigrationNotes notes = new MigrationNotes();
        Map<String, CloneOrigin> origins = Map.of(
                "harbour", CloneOrigin.at(RemoteAddress.of("git@forge.example.org:team/harbour.git").orElseThrow()),
                "beacon", CloneOrigin.at(RemoteAddress.of("https://forge.example.org/other-team/beacon.git").orElseThrow()),
                "ledger", CloneOrigin.noRemote(),
                "EDI", CloneOrigin.at(RemoteAddress.of("https://github.com/example-owner/EDI.git").orElseThrow()));

        List<NewProject> added = registryImport(notes, List.of("harbour", "EDI")).additions(List.of(
                new ForkProject("lighthouse", "main"),
                new ForkProject("harbour", "master"),
                new ForkProject("beacon", null),
                new ForkProject("ledger", "main"),
                new ForkProject("firstofficer", "main"),
                new ForkProject("EDI", "main")), origins::get);

        assertThat(added).containsExactly(
                new NewProject(new ProjectName("harbour"), new RegisteredForge(new ForgeRepository(ForgeKind.GITLAB, "forge.example.org", "team/harbour"),
                        "keeper"), new BaseBranch("master"), DeliveryMode.DRAFT_REVIEW, null, null, true),
                new NewProject(new ProjectName("beacon"), null, null, DeliveryMode.DRAFT_REVIEW, null, null, false),
                new NewProject(new ProjectName("ledger"), null, new BaseBranch("main"), DeliveryMode.LOCAL_BRANCH, null, null, false),
                new NewProject(new ProjectName("EDI"), null, new BaseBranch("main"), DeliveryMode.DRAFT_REVIEW, TrackerKind.EDI, new ProjectKey("EDI"),
                        true));
        assertThat(notes.all()).extracting(MigrationNote::subject).containsExactly("harbour", "beacon", "beacon", "beacon", "ledger", "ledger", "EDI");
        assertThat(notes.all()).extracting(MigrationNote::subject, MigrationNote::note).contains(
                tuple("beacon", "lives at https://forge.example.org/other-team/beacon, but no registered project there names the account I work"
                        + " under; add forge: { kind: gitlab, host: forge.example.org, path: other-team/beacon, account: <account> } to its entry in"
                        + " config/projects.yaml"),
                tuple("beacon", "names no base branch; add base: to its entry in config/projects.yaml"),
                tuple("ledger", "has no remote, so its work arrives as a branch in your checkout; add captainCheckout: to its entry in config/projects.yaml"),
                tuple("EDI", "lives at https://github.com/example-owner/EDI, but no registered project there names the account I work under; add"
                        + " forge: { kind: github, host: github.com, path: example-owner/EDI, account: <account> } to its entry in config/projects.yaml"));
    }

    @Test
    void writesTheAdditionsBelowTheProjectsEdiAlreadyHas() throws IOException {
        ProjectRegistry registry = registered();
        String content = registry.contentWith(List.of(new NewProject(new ProjectName("ledger"), null, new BaseBranch("main"), DeliveryMode.LOCAL_BRANCH,
                TrackerKind.EDI, new ProjectKey("LDG"), true)));
        Files.writeString(home.resolve("config/projects.yaml"), content);

        assertThat(content).as("the registry as the captain wrote it, then the new entry").startsWith(REGISTERED)
                .endsWith("  - name: ledger\n    tracker:\n      kind: edi\n      projectKey: LDG\n    base: main\n    delivery: local-branch\n"
                        + "    craftRules: true\n");
        assertThat(registry.contentWith(List.of())).isEqualTo(content);
        assertThat(registry.entries()).extracting(entry -> entry.project().name().value(), entry -> entry.project().workKeys(), entry -> entry.delivery())
                .containsExactly(tuple("lighthouse", new ProjectKey("LHT"), DeliveryMode.DRAFT_REVIEW),
                        tuple("ledger", new ProjectKey("LDG"), DeliveryMode.LOCAL_BRANCH));
        assertThat(registry.entryOf(new ProjectName("ledger")).craftRules()).isTrue();
    }

    @Test
    void rewritesARegistryItCannotAppendTo() throws IOException {
        Files.createDirectories(home.resolve("config"));
        Files.writeString(home.resolve("config/projects.yaml"), "projects: [{ name: lighthouse, tracker: { kind: edi, projectKey: LHT } }]\n");
        ProjectRegistry registry = new ProjectRegistry(new EdiHome(home), new MachineProfile(new EdiHome(home)));

        Files.writeString(home.resolve("config/projects.yaml"), registry.contentWith(List.of(new NewProject(new ProjectName("ledger"), null, null,
                DeliveryMode.LOCAL_BRANCH, null, null, false))));

        assertThat(registry.projects()).extracting(project -> project.name().value()).containsExactly("lighthouse", "ledger");
    }

    private RegistryImport registryImport(MigrationNotes notes, List<String> craftRuleProjects) throws IOException {
        return new RegistryImport(registered().entries(), craftRuleProjects, notes);
    }

    private ProjectRegistry registered() throws IOException {
        Files.createDirectories(home.resolve("config"));
        if (!Files.exists(home.resolve("config/projects.yaml"))) {
            Files.writeString(home.resolve("config/projects.yaml"), REGISTERED);
        }
        return new ProjectRegistry(new EdiHome(home), new MachineProfile(new EdiHome(home)));
    }
}
