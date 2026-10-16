package io.github.ythibos.edi.application.worker;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ythibos.edi.adapter.store.WorkerStore;
import io.github.ythibos.edi.domain.worker.Worker;

@Service
public class WorkerTokens {

    private static final String TOKEN_FILE = "token";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
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
        byte[] random = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Path file = fileIn(stateDirectory);
        write(file, token);
        return new IssuedToken(file, digestOf(token));
    }

    public Optional<Worker> workerHolding(String presented) {
        return transaction.execute(_ -> workers.holdingToken(digestOf(presented)));
    }

    private static String digestOf(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
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
