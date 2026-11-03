package io.github.ythibos.edi.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.publish.FindingPlace;
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class SyntheticSecretsTest {

    private static final int KEYS = 5000;

    private final Gitleaks gitleaks = new Gitleaks(new ExternalPrograms(), new JsonMapper());

    @TempDir
    private Path directory;

    @Test
    void gitleaksRecognisesEverySyntheticAwsAccessKey() throws Exception {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
        List<String> keys = Stream.generate(SyntheticSecrets::awsAccessKeyId).limit(KEYS).toList();

        List<String> missedAmongTheOthers = keysMissedInOneCommit(keys);

        assertThat(missedAmongTheOthers).allSatisfy(key -> assertThat(gitleaks.secretsIn(new RequestText("A cloud key", "cloud.key=" + key)))
                .as("gitleaks on %s alone", key)
                .hasSize(1));
    }

    private List<String> keysMissedInOneCommit(List<String> keys) throws IOException {
        FixtureRepository repository = FixtureRepository.initialise(directory)
                .write("README.md", "A project.\n")
                .commit("Start the project")
                .branch("keys")
                .write("config/keys.properties", keys.stream().map(key -> "cloud.key=" + key + "\n").collect(Collectors.joining()))
                .commit("Add the cloud keys");
        Set<Integer> foundLines = gitleaks.secretsIn(repository.root(), new RevisionRange("main..keys")).stream()
                .map(finding -> ((FindingPlace.Line) finding.place()).number())
                .collect(Collectors.toSet());
        return IntStream.range(0, keys.size()).filter(index -> !foundLines.contains(index + 1)).mapToObj(keys::get).toList();
    }
}
