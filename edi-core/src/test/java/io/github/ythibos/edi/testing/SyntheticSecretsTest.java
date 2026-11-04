package io.github.ythibos.edi.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.gitleaks.Gitleaks;
import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.domain.publish.FindingPlace;
import io.github.ythibos.edi.domain.publish.RequestText;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class SyntheticSecretsTest {

    private static final int KEYS = 5000;
    private static final long SEED_SPELLING_FALSE_IN_AN_AWS_KEY = 2_922_155;
    private static final long SEED_ENDING_A_GITHUB_TOKEN_IN_NULL = 2_000_388;
    private static final int DRAWS = 10_000;
    private static final GitleaksAllowlists ALLOWLISTS = GitleaksAllowlists.ofTheDefaultConfiguration();

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

    @Test
    void gitleaksRecognisesTheAwsAccessKeyOfADrawSpellingFalse() {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
        String key = SyntheticSecrets.seeded(SEED_SPELLING_FALSE_IN_AN_AWS_KEY).nextAwsAccessKeyId();

        assertThat(gitleaks.secretsIn(new RequestText("A cloud key", "cloud.key=" + key))).as("gitleaks on %s", key).hasSize(1);
    }

    @Test
    void gitleaksRecognisesTheGithubTokenOfADrawEndingInNull() {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");
        String token = SyntheticSecrets.seeded(SEED_ENDING_A_GITHUB_TOKEN_IN_NULL).nextGithubToken();

        assertThat(gitleaks.secretsIn(new RequestText("A forge token", "forge.token=" + token))).as("gitleaks on %s", token).hasSize(1);
    }

    @Test
    void noDrawYieldsAnAwsAccessKeyGitleaksAllowlists() {
        assertThat(firstDrawsOfSeedsAround(SEED_SPELLING_FALSE_IN_AN_AWS_KEY, SyntheticSecrets::nextAwsAccessKeyId))
                .hasSize(DRAWS)
                .filteredOn(ALLOWLISTS::allow)
                .as("drawn keys gitleaks' global allowlist lets through")
                .isEmpty();
    }

    @Test
    void noDrawYieldsAGithubTokenGitleaksAllowlists() {
        assertThat(firstDrawsOfSeedsAround(SEED_ENDING_A_GITHUB_TOKEN_IN_NULL, SyntheticSecrets::nextGithubToken))
                .hasSize(DRAWS)
                .filteredOn(ALLOWLISTS::allow)
                .as("drawn tokens gitleaks' global allowlist lets through")
                .isEmpty();
    }

    @Test
    void theAllowlistsAreThoseOfTheInstalledGitleaks() {
        assumeTrue(InstalledPrograms.answers("gitleaks", "version"), "gitleaks is not installed");

        assertThat(new ExternalPrograms().run(Program.GITLEAKS, directory, List.of("version")).outputOfSuccess().strip())
                .as("the version of the default configuration in gitleaks/default-allowlists.toml, copied again on every move of gitleaks")
                .isEqualTo(ALLOWLISTS.version());
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

    private static List<String> firstDrawsOfSeedsAround(long seed, Function<SyntheticSecrets, String> draw) {
        return LongStream.range(seed - DRAWS / 2, seed + DRAWS / 2).mapToObj(SyntheticSecrets::seeded).map(draw).toList();
    }
}
