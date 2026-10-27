package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.InstalledPrograms;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestProcesses;

@Tag("adapter")
class InstallTest {

    private static final Path REPOSITORY = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final String MAVEN_NOTING_ITS_JAVA = """
            #!/bin/sh
            echo "$JAVA_HOME" > java-home-of-the-build
            mkdir -p edi-cli/target edi-core/target
            touch edi-cli/target/edi edi-core/target/edi-core
            """;
    private static final String UNINSTALLED_TOOLCHAIN = """
            [tools]
            java = "graalvm-community-0.0.1"
            """;

    @RegisterExtension
    private final TestProcesses processes = new TestProcesses();

    @TempDir
    private Path directory;

    @Test
    void buildsWithTheJavaTheCheckoutPinsWhateverTheCallersDirectoryAndJavaHome() throws IOException, InterruptedException {
        Path checkout = checkoutPinning(Files.readString(REPOSITORY.resolve("mise.toml")));
        assumeTrue(everyPinnedToolIsInstalled(checkout), "mise or a tool the checkout pins is not installed");

        InstallRun install = install(checkout, System.getenv("PATH"));

        assertThat(install.exitCode()).as(install.output()).isZero();
        assertThat(Path.of(Files.readString(javaHomeOfTheBuild(checkout)).strip(), "bin", "native-image")).isExecutable();
        assertThat(directory.resolve("bin/edi")).isExecutable();
    }

    @Test
    void installsTheLauncherAndRecordsTheCommitItBuilt() throws IOException, InterruptedException {
        Path checkout = checkoutPinning(Files.readString(REPOSITORY.resolve("mise.toml")));
        assumeTrue(everyPinnedToolIsInstalled(checkout), "mise or a tool the checkout pins is not installed");

        InstallRun install = install(checkout, System.getenv("PATH"));

        assertThat(install.exitCode()).as(install.output()).isZero();
        assertThat(directory.resolve("bin/helloedi")).isExecutable();
        assertThat(directory.resolve("home/state/installed-build")).hasContent(FixtureRepository.at(checkout).head());
    }

    @Test
    void refusesToBuildWhenThePinnedToolchainIsNotInstalled() throws IOException, InterruptedException {
        assumeTrue(InstalledPrograms.answers("mise", "--version"), "mise is not installed");
        Path checkout = checkoutPinning(UNINSTALLED_TOOLCHAIN);

        InstallRun install = install(checkout, System.getenv("PATH"));

        assertThat(install.exitCode()).isEqualTo(1);
        assertThat(install.output()).contains("part of it is not installed; run mise install in " + checkout).contains("graalvm-community-0.0.1");
        assertThat(javaHomeOfTheBuild(checkout)).doesNotExist();
    }

    @Test
    void refusesToBuildWhenMiseIsNotOnThePath() throws IOException, InterruptedException {
        Path checkout = checkoutPinning(Files.readString(REPOSITORY.resolve("mise.toml")));

        InstallRun install = install(checkout, programsWithoutMise().toString());

        assertThat(install.exitCode()).isEqualTo(1);
        assertThat(install.output()).contains("mise, which runs it, is not on the PATH; install mise, run mise install in " + checkout);
        assertThat(javaHomeOfTheBuild(checkout)).doesNotExist();
    }

    private Path checkoutPinning(String toolchain) throws IOException {
        Path checkout = directory.resolve("EDI");
        for (String script : List.of("ops/install.sh", "ops/helloedi")) {
            StandInScripts.installed(checkout.resolve(script), Files.readString(REPOSITORY.resolve(script)));
        }
        StandInScripts.installed(checkout.resolve("mvnw"), MAVEN_NOTING_ITS_JAVA);
        FixtureRepository.initialise(checkout).write("mise.toml", toolchain).commit("Pin the toolchain");
        return checkout;
    }

    private static boolean everyPinnedToolIsInstalled(Path checkout) throws InterruptedException {
        try {
            Process listing = new ProcessBuilder("mise", "ls", "--local", "--missing", "--no-header").directory(checkout.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String missing = new String(listing.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return listing.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS) && listing.exitValue() == 0 && missing.isBlank();
        } catch (IOException miseIsMissing) {
            return false;
        }
    }

    private InstallRun install(Path checkout, String searchPath) throws IOException, InterruptedException {
        ProcessBuilder launch = new ProcessBuilder(checkout.resolve("ops/install.sh").toString())
                .directory(Files.createDirectories(directory.resolve("elsewhere")).toFile())
                .redirectErrorStream(true);
        launch.environment().put("PATH", searchPath);
        launch.environment().put("JAVA_HOME", Files.createDirectories(directory.resolve("a-java-without-native-image")).toString());
        launch.environment().put("EDI_HOME", directory.resolve("home").toString());
        launch.environment().put("EDI_BIN", directory.resolve("bin").toString());
        launch.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        launch.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        Process install = processes.register(launch.start());
        install.getOutputStream().close();
        String output = new String(install.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!install.waitFor(PATIENCE.toSeconds(), TimeUnit.SECONDS)) {
            install.destroyForcibly();
            throw new IOException("ops/install.sh did not finish within " + PATIENCE.toSeconds() + " seconds");
        }
        return new InstallRun(install.exitValue(), output);
    }

    private static Path javaHomeOfTheBuild(Path checkout) {
        return checkout.resolve("java-home-of-the-build");
    }

    private Path programsWithoutMise() throws IOException {
        Path programs = directory.resolve("programs");
        for (String program : List.of("bash", "dirname")) {
            StandInScripts.installed(programs.resolve(program), "#!/bin/sh\nexec '" + onThePath(program) + "' \"$@\"\n");
        }
        return programs;
    }

    private static Path onThePath(String program) {
        return Stream.of(System.getenv("PATH").split(File.pathSeparator)).map(entry -> Path.of(entry, program)).filter(Files::isExecutable)
                .findFirst().orElseThrow();
    }

    private record InstallRun(int exitCode, String output) {
    }
}
