package io.github.ythibos.edi.adapter.browser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

final class HandOffPages {

    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private final Path directory;
    private final Executor removal;

    HandOffPages(Path directory, Duration lifetime) {
        this.directory = directory;
        this.removal = CompletableFuture.delayedExecutor(lifetime.toMillis(), TimeUnit.MILLISECONDS);
    }

    Path leading(URI page) {
        Path handOff = written(page);
        removal.execute(() -> remove(handOff));
        return handOff;
    }

    private Path written(URI page) {
        try {
            Files.createDirectories(directory, OWNER_ONLY_DIRECTORY);
            Path handOff = Files.createTempFile(directory, "sign-in-", ".html", OWNER_ONLY);
            Files.writeString(handOff, redirectTo(page));
            return handOff;
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static String redirectTo(URI page) {
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><meta name="referrer" content="no-referrer">\
                <meta http-equiv="refresh" content="0;url=%s"><title>EDI console</title></head></html>
                """.formatted(asAttribute(page.toASCIIString()));
    }

    private static String asAttribute(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean remove(Path handOff) {
        try {
            return Files.deleteIfExists(handOff);
        } catch (IOException unremovable) {
            return false;
        }
    }
}
