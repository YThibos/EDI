package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.domain.AsciiCharacters;

@Component
public class ClaudeSessions {

    private static final String TRANSCRIPT_SUFFIX = ".jsonl";
    private static final char FOLDER_SEPARATOR = '-';

    private final Path sessions;

    public ClaudeSessions(@Value("${edi.workers.claude-sessions}") Path sessions) {
        this.sessions = sessions;
    }

    public Optional<Path> transcriptOf(UUID session) {
        if (!Files.isDirectory(sessions)) {
            return Optional.empty();
        }
        try (Stream<Path> folders = Files.list(sessions)) {
            return folders.map(folder -> folder.resolve(session + TRANSCRIPT_SUFFIX)).filter(Files::isRegularFile).findFirst();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    public void carryInto(Path transcript, Path workingDirectory) {
        Path carried = sessions.resolve(folderOf(workingDirectory)).resolve(transcript.getFileName());
        if (Files.exists(carried)) {
            return;
        }
        try {
            Files.createDirectories(carried.getParent());
            Files.copy(transcript, carried);
        } catch (IOException uncopied) {
            throw new UncheckedIOException(uncopied);
        }
    }

    static String folderOf(Path workingDirectory) {
        return workingDirectory.toAbsolutePath().normalize().toString().chars()
                .map(character -> AsciiCharacters.isLetterOrDigit(character) ? character : FOLDER_SEPARATOR)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
