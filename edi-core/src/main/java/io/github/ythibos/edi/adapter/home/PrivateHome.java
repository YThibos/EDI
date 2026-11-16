package io.github.ythibos.edi.adapter.home;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;

@Component(PrivateHome.BEAN)
public class PrivateHome {

    public static final String BEAN = "privateHome";

    private static final Logger LOG = LoggerFactory.getLogger(PrivateHome.class);
    private static final String POSIX = "posix";
    private static final List<String> KEPT_BY_THE_CORE = List.of("config", "data", "memory", "state");
    private static final Set<PosixFilePermission> OTHER_USERS = EnumSet.of(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);

    private final Optional<String> repair;

    public PrivateHome(EdiHome home) {
        this.repair = madePrivate(home.root());
        repair.ifPresent(LOG::warn);
    }

    public Optional<String> repair() {
        return repair;
    }

    private static Optional<String> madePrivate(Path root) {
        if (!root.getFileSystem().supportedFileAttributeViews().contains(POSIX)) {
            return Optional.empty();
        }
        createdForItsOwnerAlone(root);
        long closed = Stream.concat(Stream.of(root), keptByTheCore(root)).filter(PrivateHome::closedToOtherUsers).count();
        return closed == 0 ? Optional.empty() : Optional.of(reportOf(root, closed));
    }

    public static void createdForItsOwnerAlone(Path root) {
        if (Files.isDirectory(root)) {
            return;
        }
        try {
            Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (IOException uncreatable) {
            throw new UncheckedIOException(uncreatable);
        }
    }

    private static Stream<Path> keptByTheCore(Path root) {
        return KEPT_BY_THE_CORE.stream().map(root::resolve).filter(Files::isDirectory).flatMap(PrivateHome::everythingIn);
    }

    private static Stream<Path> everythingIn(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .toList().stream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static boolean closedToOtherUsers(Path path) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!permissions.removeAll(OTHER_USERS)) {
                return false;
            }
            Files.setPosixFilePermissions(path, permissions);
            return true;
        } catch (IOException unchangeable) {
            throw new UncheckedIOException(unchangeable);
        }
    }

    private static String reportOf(Path root, long closed) {
        return "The EDI home " + root + " held " + closed + (closed == 1 ? " directory or file" : " directories and files")
                + " that other users could read or enter; each is now open to its owner alone.";
    }
}
