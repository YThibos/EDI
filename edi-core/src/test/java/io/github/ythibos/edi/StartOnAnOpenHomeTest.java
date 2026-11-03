package io.github.ythibos.edi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("adapter")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class StartOnAnOpenHomeTest {

    private static final String OPEN_DIRECTORY = "rwxr-xr-x";
    private static final String OPEN_FILE = "rw-r--r--";

    @TempDir
    private static Path temporary;

    private static Path home;

    @BeforeAll
    static void leaveAnExistingHomeOpenToOtherUsers() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "the file system has no POSIX permissions");
        home = temporary.resolve("home");
        Files.createDirectories(home.resolve("memory"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OPEN_DIRECTORY)));
        Files.setPosixFilePermissions(home, PosixFilePermissions.fromString(OPEN_DIRECTORY));
        Files.writeString(home.resolve("memory/learnings.md"), "# Learnings\n");
        Files.setPosixFilePermissions(home.resolve("memory/learnings.md"), PosixFilePermissions.fromString(OPEN_FILE));
    }

    @DynamicPropertySource
    static void useTheOpenHome(DynamicPropertyRegistry properties) {
        properties.add("edi.home", () -> home.toString());
    }

    @Test
    void leavesTheHomeAndWhatItKeepsThereToTheCaptainAlone() throws IOException {
        assertThat(permissionsOf(home)).isEqualTo("rwx------");
        assertThat(permissionsOf(home.resolve("memory"))).isEqualTo("rwx------");
        assertThat(permissionsOf(home.resolve("memory/learnings.md"))).isEqualTo("rw-------");
    }

    private static String permissionsOf(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }
}
