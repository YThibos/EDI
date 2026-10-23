package io.github.ythibos.edi.adapter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("adapter")
class CommitKeysTest {

    private static final String HARNESS = "git@forge.example.org:example-owner/harness.git";
    private static final String OTHER_SETTINGS = """
            # no-mistakes global configuration
            agent: auto

            # Auto-fix commit subject template.
            # commit:
            #   fix_message: "no-mistakes({{.Step}}): {{.Summary}}"
            """;
    private static final String PROVIDERS = """
            providers:
              gitlab:
                draft_pull_requests: true
            """;
    private static final String KEYED_HARNESS = """
              git@forge.example.org:example-owner/harness.git:
                commit:
                  branch_pattern: "^(?:feature|chore|research)/([A-Z][A-Z0-9]*-[0-9]+)(?:-[0-9]+)?$"
                  fix_message: "{{.Branch}} {{.Summary}}"
                pr:
                  title_format: "{{.Branch}} {{.Title}}"
            """;
    private static final String OTHER_REPOSITORY = """
              https://github.com/example-owner/lantern:
                commit:
                  fix_message: "LAN {{.Summary}}"
            """;

    @TempDir
    private Path home;

    @Test
    void addsTheRepositorysKeyedCommitsToAConfigurationWithoutOverrides() throws IOException {
        Path configuration = written(OTHER_SETTINGS + PROVIDERS);

        CommitKeys.keyCommitsPushedTo(configuration, HARNESS);

        assertThat(configuration).content().isEqualTo(OTHER_SETTINGS + PROVIDERS + "\nrepository_overrides:\n" + KEYED_HARNESS);
        assertThat(Files.getPosixFilePermissions(configuration)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    void replacesTheRepositorysEntryUnderAnySpellingOfItsRemoteAndKeepsEveryOtherRepository() throws IOException {
        Path configuration = written(OTHER_SETTINGS + """

                # Set by hand until EDI owned it.
                repository_overrides:
                  ssh://git@Forge.Example.org/example-owner/Harness:
                    commit:
                      fix_message: "HRN {{.Summary}}"
                """ + OTHER_REPOSITORY + "\n" + PROVIDERS);

        CommitKeys.keyCommitsPushedTo(configuration, HARNESS);

        assertThat(configuration).content().isEqualTo(OTHER_SETTINGS + """

                # Set by hand until EDI owned it.
                repository_overrides:
                """ + OTHER_REPOSITORY + KEYED_HARNESS + "\n" + PROVIDERS);
    }

    @Test
    void leavesAConfigurationThatAlreadyKeysTheRepositorysCommitsUntouched() throws IOException {
        String keyed = OTHER_SETTINGS + "repository_overrides:\n" + KEYED_HARNESS + OTHER_REPOSITORY;
        Path configuration = written(keyed);

        CommitKeys.keyCommitsPushedTo(configuration, HARNESS);

        assertThat(configuration).content().isEqualTo(keyed);
    }

    @Test
    void writesNoConfigurationThePipelineHasNotCreated() {
        Path configuration = home.resolve("config.yaml");

        CommitKeys.keyCommitsPushedTo(configuration, HARNESS);

        assertThat(configuration).doesNotExist();
    }

    private Path written(String content) throws IOException {
        Path configuration = Files.writeString(home.resolve("config.yaml"), content);
        Files.setPosixFilePermissions(configuration, PosixFilePermissions.fromString("rw-------"));
        return configuration;
    }
}
