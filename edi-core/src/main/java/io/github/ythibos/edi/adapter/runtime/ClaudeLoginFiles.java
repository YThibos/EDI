package io.github.ythibos.edi.adapter.runtime;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.application.port.WorkerRuntime.Login;

@Component
public class ClaudeLoginFiles {

    private static final String GLOBAL_CONFIGURATION = ".claude.json";
    private static final String CLAUDE_CODES_OWN_DIRECTORY = ".claude";
    private static final String SESSIONS = "projects";

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

    private Path configDirectoryOf(Login login) {
        return login.keepsClaudeCodesOwnDirectory() ? claudeCodesHome.resolve(CLAUDE_CODES_OWN_DIRECTORY) : login.configDirectory();
    }
}
