package io.github.ythibos.edi.decisions;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.toml.TomlMapper;

class GraalVmToolchainTest {

    private static final Path MISE_TOML = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize().resolve("mise.toml");
    private static final String GRAALVM = "github:graalvm/graalvm-ce-builds";
    private static final Pattern RELEASE_NUMBER = Pattern.compile("\\d+\\.\\d+");

    @Test
    void readsTheVersionFromTheGraalReleaseTags() {
        JsonNode graalVm = graalVm();

        assertThat(graalVm.path("version").asString()).matches("\\d+(?:\\.\\d+)+");
        assertThat(graalVm.path("version_prefix").asString()).isEqualTo("graal-");
    }

    @Test
    void picksEveryPlatformsArchiveByAPatternThatNamesNoVersion() {
        List<JsonNode> platforms = graalVm().path("platforms").valueStream().toList();

        assertThat(platforms).isNotEmpty().allSatisfy(platform -> {
            assertThat(platform.propertyNames()).containsExactly("asset_pattern");
            assertThat(platform.path("asset_pattern").asString()).doesNotContainPattern(RELEASE_NUMBER);
        });
    }

    @Test
    void setsJavaHomeFromTheInstalledGraalVm() {
        assertThat(miseConfiguration().path("env").path("JAVA_HOME").path("value").asString()).isEqualTo("{{ tools['" + GRAALVM + "'].path }}");
    }

    private static JsonNode graalVm() {
        return miseConfiguration().path("tools").path(GRAALVM);
    }

    private static JsonNode miseConfiguration() {
        return new TomlMapper().readTree(MISE_TOML.toFile());
    }
}
