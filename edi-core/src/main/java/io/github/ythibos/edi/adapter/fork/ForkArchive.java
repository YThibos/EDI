package io.github.ythibos.edi.adapter.fork;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class ForkArchive {

    private static final String DATA = "data";
    private static final String TASK_FOLDERS = "tasks/";
    private static final String CONFIGURATION = "config";
    private static final String HELD_WORK = "state/held";
    private static final String SEPARATOR = "/";

    private ForkArchive() {
    }

    static List<ArchivedFile> of(Path root) {
        return Stream.of(dataFiles(root.resolve(DATA)), filesUnder(root.resolve(CONFIGURATION), CONFIGURATION + SEPARATOR),
                        filesUnder(root.resolve(HELD_WORK), HELD_WORK + SEPARATOR))
                .flatMap(List::stream)
                .sorted(Comparator.comparing(ArchivedFile::path))
                .toList();
    }

    private static List<ArchivedFile> dataFiles(Path data) {
        return childrenOf(data).stream()
                .flatMap(child -> Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        ? filesUnder(child, TASK_FOLDERS + child.getFileName() + SEPARATOR).stream()
                        : Stream.of(new ArchivedFile(child, child.getFileName().toString())).filter(file -> isCopied(file.source())))
                .toList();
    }

    private static List<ArchivedFile> filesUnder(Path directory, String prefix) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(ForkArchive::isCopied)
                    .map(file -> new ArchivedFile(file, prefix + relativePath(directory, file)))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<Path> childrenOf(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static boolean isCopied(Path file) {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file);
    }

    private static String relativePath(Path directory, Path file) {
        return directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), SEPARATOR);
    }
}
