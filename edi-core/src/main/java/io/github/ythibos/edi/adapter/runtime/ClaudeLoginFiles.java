package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.application.port.WorkerRuntime.LoginMismatch;

@Component
public class ClaudeLoginFiles {

    private static final String GLOBAL_CONFIGURATION = ".claude.json";
    private static final String CLAUDE_CODES_OWN_DIRECTORY = ".claude";
    private static final String SESSIONS = "projects";
    private static final String CREDENTIALS = ".credentials.json";
    private static final int TRANSCRIPT_DEPTH_IN_ITS_DIRECTORY = 3;

    private final Path claudeCodesHome;

    public ClaudeLoginFiles(@Value("${edi.workers.claude-home}") Path claudeCodesHome) {
        this.claudeCodesHome = claudeCodesHome;
    }

    Path globalConfigurationOf(Login login) {
        return login.keepsClaudeCodesOwnDirectory() ? claudeCodesHome.resolve(GLOBAL_CONFIGURATION) : login.configDirectory().resolve(GLOBAL_CONFIGURATION);
    }

    Path sessionsOf(Login login) {
        return configDirectoryOf(login).resolve(SESSIONS);
    }

    Path credentialsOf(Login login) {
        return configDirectoryOf(login).resolve(CREDENTIALS);
    }

    Optional<LoginMismatch> mismatchOf(Path transcript, Login login) {
        Path ranIn = configDirectoryHolding(transcript.toAbsolutePath().normalize());
        Path shouldHaveRunIn = configDirectoryOf(login).toAbsolutePath().normalize();
        return isTheSameDirectory(ranIn, shouldHaveRunIn) ? Optional.empty() : Optional.of(new LoginMismatch(ranIn, shouldHaveRunIn));
    }

    private Path configDirectoryOf(Login login) {
        return login.keepsClaudeCodesOwnDirectory() ? claudeCodesHome.resolve(CLAUDE_CODES_OWN_DIRECTORY) : login.configDirectory();
    }

    private static Path configDirectoryHolding(Path transcript) {
        int depth = transcript.getNameCount() - TRANSCRIPT_DEPTH_IN_ITS_DIRECTORY;
        return depth > 0 ? transcript.getRoot().resolve(transcript.subpath(0, depth)) : transcript.getRoot();
    }

    private static boolean isTheSameDirectory(Path ranIn, Path shouldHaveRunIn) {
        try {
            return ranIn.equals(shouldHaveRunIn) || Files.isDirectory(ranIn) && Files.isDirectory(shouldHaveRunIn) && Files.isSameFile(ranIn, shouldHaveRunIn);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
