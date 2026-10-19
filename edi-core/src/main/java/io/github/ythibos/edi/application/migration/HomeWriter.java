package io.github.ythibos.edi.application.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.fork.ArchivedFile;

@Component
public class HomeWriter {

    public static final String KEPT_SUFFIX = ".before-migration";
    private static final Set<PosixFilePermission> READ_ONLY_TO_ITS_OWNER = PosixFilePermissions.fromString("r--------");

    private final EdiHome home;

    public HomeWriter(EdiHome home) {
        this.home = home;
    }

    HomeSurvey survey(HomeImport planned) {
        return new HomeSurvey(
                home.root(),
                planned.files().stream().map(file -> new FileChange(file.path(), file.holds(), changeOf(file))).toList(),
                archiveSurveyOf(planned.archive()),
                planned.links().stream().map(link -> new FileChange(link.path(), "the old folder of " + link.task(), changeOf(link))).toList());
    }

    void write(HomeImport planned) {
        planned.files().forEach(this::written);
        planned.archive().copies().forEach(this::copied);
        planned.links().forEach(this::linked);
    }

    private HomeChange changeOf(HomeFile file) {
        Path target = home.root().resolve(file.path());
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return HomeChange.ADDED;
        }
        return Arrays.equals(bytesIn(target), file.content().getBytes(StandardCharsets.UTF_8)) ? HomeChange.UNCHANGED : HomeChange.REPLACED;
    }

    private ArchiveSurvey archiveSurveyOf(ArchivePlan archive) {
        List<ArchivedFile> copies = archive.copies();
        Map<HomeChange, Long> changes = copies.stream().collect(Collectors.groupingBy(this::changeOf, Collectors.counting()));
        return new ArchiveSurvey(archive.into(), counted(changes, HomeChange.ADDED), counted(changes, HomeChange.UNCHANGED),
                counted(changes, HomeChange.REPLACED), bytesOf(copies));
    }

    private HomeChange changeOf(FolderLink link) {
        Path target = home.root().resolve(link.path());
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return HomeChange.ADDED;
        }
        return Files.isSymbolicLink(target) && linkTargetOf(target).equals(Path.of(link.target())) ? HomeChange.UNCHANGED : HomeChange.REPLACED;
    }

    private void written(HomeFile file) {
        Path target = home.root().resolve(file.path());
        HomeChange change = changeOf(file);
        if (change == HomeChange.UNCHANGED) {
            return;
        }
        try {
            prepare(target, change);
            Files.writeString(target, file.content(), StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void copied(ArchivedFile file) {
        Path target = home.root().resolve(file.path());
        if (changeOf(file) == HomeChange.UNCHANGED) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.source(), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
            if (!Files.isSymbolicLink(target)) {
                Files.setPosixFilePermissions(target, READ_ONLY_TO_ITS_OWNER);
            }
        } catch (IOException uncopied) {
            throw new UncheckedIOException(uncopied);
        }
    }

    private void linked(FolderLink link) {
        Path target = home.root().resolve(link.path());
        HomeChange change = changeOf(link);
        if (change == HomeChange.UNCHANGED) {
            return;
        }
        try {
            prepare(target, change);
            Files.createSymbolicLink(target, Path.of(link.target()));
        } catch (IOException unlinked) {
            throw new UncheckedIOException(unlinked);
        }
    }

    private static byte[] bytesIn(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private HomeChange changeOf(ArchivedFile file) {
        Path target = home.root().resolve(file.path());
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return HomeChange.ADDED;
        }
        return isTheSame(file.source(), target) ? HomeChange.UNCHANGED : HomeChange.REPLACED;
    }

    private static int counted(Map<HomeChange, Long> changes, HomeChange change) {
        return changes.getOrDefault(change, 0L).intValue();
    }

    private static long bytesOf(List<ArchivedFile> copies) {
        return copies.stream().filter(file -> Files.isRegularFile(file.source(), LinkOption.NOFOLLOW_LINKS)).mapToLong(file -> sizeOf(file.source()))
                .sum();
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Path linkTargetOf(Path link) {
        try {
            return Files.readSymbolicLink(link);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void prepare(Path target, HomeChange change) throws IOException {
        Files.createDirectories(target.getParent());
        if (change == HomeChange.REPLACED) {
            Files.move(target, target.resolveSibling(target.getFileName() + KEPT_SUFFIX), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isTheSame(Path source, Path copy) {
        if (Files.isSymbolicLink(source)) {
            return Files.isSymbolicLink(copy) && linkTargetOf(source).equals(linkTargetOf(copy));
        }
        try {
            return Files.isRegularFile(copy, LinkOption.NOFOLLOW_LINKS) && Files.size(source) == Files.size(copy)
                    && Files.getLastModifiedTime(source).equals(Files.getLastModifiedTime(copy));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
