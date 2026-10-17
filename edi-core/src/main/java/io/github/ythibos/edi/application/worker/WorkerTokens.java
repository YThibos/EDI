package io.github.ythibos.edi.application.worker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.application.access.Tokens;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class WorkerTokens {

    private static final String TOKEN_FILE = "token";
    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private final WorkerStore workers;
    private final TransactionTemplate transaction;

    public WorkerTokens(WorkerStore workers, TransactionTemplate transaction) {
        this.workers = workers;
        this.transaction = transaction;
    }

    private static Path fileIn(Path stateDirectory) {
        return stateDirectory.resolve(TOKEN_FILE);
    }

    public IssuedToken issueInto(Path stateDirectory) {
        String token = Tokens.drawn();
        Path file = fileIn(stateDirectory);
        write(file, token);
        return new IssuedToken(file, Tokens.digestOf(token));
    }

    public Optional<Worker> workerHolding(String presented) {
        return transaction.execute(_ -> workers.holdingToken(Tokens.digestOf(presented)));
    }

    private static void write(Path file, String token) {
        try {
            Files.createDirectories(file.getParent());
            Files.deleteIfExists(file);
            Files.writeString(Files.createFile(file, OWNER_ONLY), token, StandardCharsets.US_ASCII);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    public record IssuedToken(Path file, String digest) {
    }
}
