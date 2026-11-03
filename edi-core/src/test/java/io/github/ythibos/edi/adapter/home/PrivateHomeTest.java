package io.github.ythibos.edi.adapter.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;

class PrivateHomeTest {

    private static final String OPEN_DIRECTORY = "rwxr-xr-x";
    private static final String OPEN_FILE = "rw-r--r--";
    private static final String OPEN_SCRIPT = "rwxr-xr-x";

    @TempDir
    private Path temporary;

    private Path home;

    @BeforeEach
    void requirePosixPermissions() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "the file system has no POSIX permissions");
        home = temporary.resolve("home");
    }

    @Test
    void createsAMissingHomeForTheCaptainAloneWithoutReportingARepair() throws IOException {
        PrivateHome created = new PrivateHome(new EdiHome(home));

        assertThat(permissionsOf(home)).isEqualTo("rwx------");
        assertThat(created.repair()).isEmpty();
    }

    @Test
    void closesAnOpenHomeAndWhatTheCoreKeepsInItAndReportsTheRepairOnce() throws IOException {
        openDirectory(home.resolve("state/workers"));
        openFile(home.resolve("data/edi.db"), OPEN_FILE);
        openFile(home.resolve("config/projects.yaml"), "rw-rw-rw-");
        openFile(home.resolve("state/bin/stand-in"), OPEN_SCRIPT);

        PrivateHome repaired = new PrivateHome(new EdiHome(home));

        assertThat(repaired.repair()).hasValueSatisfying(report -> assertThat(report).startsWith("The EDI home " + home + " held 9 directories and files")
                .doesNotContain("projects.yaml"));
        assertThat(permissionsOf(home)).isEqualTo("rwx------");
        assertThat(permissionsOf(home.resolve("state/workers"))).isEqualTo("rwx------");
        assertThat(permissionsOf(home.resolve("data/edi.db"))).isEqualTo("rw-------");
        assertThat(permissionsOf(home.resolve("config/projects.yaml"))).isEqualTo("rw-------");
        assertThat(permissionsOf(home.resolve("state/bin/stand-in"))).isEqualTo("rwx------");
        assertThat(new PrivateHome(new EdiHome(home)).repair()).isEmpty();
    }

    @Test
    void leavesTheProjectsCopiesAndWhatALinkPointsAtAsTheyAre() throws IOException {
        openFile(home.resolve("worktrees/harness/lamp/README.md"), OPEN_FILE);
        Path elsewhere = openFile(temporary.resolve("elsewhere/shared.txt"), OPEN_FILE);
        Files.createDirectories(home.resolve("state"));
        Files.createSymbolicLink(home.resolve("state/shared.txt"), elsewhere);

        new PrivateHome(new EdiHome(home));

        assertThat(permissionsOf(home.resolve("worktrees/harness/lamp/README.md"))).isEqualTo(OPEN_FILE);
        assertThat(permissionsOf(elsewhere)).isEqualTo(OPEN_FILE);
    }

    private void openDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (Path opened = directory; opened.startsWith(temporary) && !opened.equals(temporary); opened = opened.getParent()) {
            Files.setPosixFilePermissions(opened, PosixFilePermissions.fromString(OPEN_DIRECTORY));
        }
    }

    private Path openFile(Path file, String permissions) throws IOException {
        openDirectory(file.getParent());
        Files.writeString(file, "lamp\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
        return file;
    }

    private static String permissionsOf(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }
}
