package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.MachineProfile;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.AsciiCharacters;
import io.github.ythibos.edi.domain.worker.LoginName;

@Component
public class ClaudeSessions {

    private static final String TRANSCRIPT_SUFFIX = ".jsonl";
    private static final char FOLDER_SEPARATOR = '-';

    private final ClaudeLoginFiles logins;
    private final MachineProfile machine;

    public ClaudeSessions(ClaudeLoginFiles logins, MachineProfile machine) {
        this.logins = logins;
        this.machine = machine;
    }

    public Optional<Path> transcriptOf(UUID session, Login login) {
        Path sessions = logins.sessionsOf(login);
        if (!Files.isDirectory(sessions)) {
            return Optional.empty();
        }
        try (Stream<Path> folders = Files.list(sessions)) {
            return folders.map(folder -> folder.resolve(session + TRANSCRIPT_SUFFIX)).filter(Files::isRegularFile).findFirst();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    public Optional<Path> transcriptOnTheDefaultLoginOf(UUID session) {
        return transcriptOf(session, theDefaultLogin());
    }

    public void carryInto(Path transcript, Path workingDirectory) {
        Path carried = logins.sessionsOf(theDefaultLogin()).resolve(folderOf(workingDirectory)).resolve(transcript.getFileName());
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

    private Login theDefaultLogin() {
        return machine.claudeLogins().named(LoginName.DEFAULT);
    }

    static String folderOf(Path workingDirectory) {
        return workingDirectory.toAbsolutePath().normalize().toString().chars()
                .map(character -> AsciiCharacters.isLetterOrDigit(character) ? character : FOLDER_SEPARATOR)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
