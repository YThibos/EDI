package io.github.ythibos.edi.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class SyntheticSecretsTest {

    private static final int KEYS = 5000;

    @TempDir
    private Path directory;

    @Test
    void gitleaksRecognisesEverySyntheticAwsAccessKey() throws Exception {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
        FixtureRepository repository = FixtureRepository.initialise(directory)
                .write("README.md", "A project.\n")
                .commit("Start the project")
                .branch("keys")
                .write("config/keys.properties", Stream.generate(SyntheticSecrets::awsAccessKeyId).limit(KEYS)
                        .map(key -> "cloud.key=" + key + "\n")
                        .collect(Collectors.joining()))
                .commit("Add the cloud keys");

        assertThat(new Gitleaks(new ExternalPrograms(), new JsonMapper()).secretsIn(repository.root(), new RevisionRange("main..keys")))
                .hasSize(KEYS);
    }
}
