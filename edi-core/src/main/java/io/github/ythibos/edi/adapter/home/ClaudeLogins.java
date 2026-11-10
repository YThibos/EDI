package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.util.Map;

import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.LoginName;

public record ClaudeLogins(Map<LoginName, Path> configDirectories) {

    public ClaudeLogins {
        configDirectories = Map.copyOf(configDirectories);
    }

    public boolean names(LoginName login) {
        return login.isTheDefault() || configDirectories.containsKey(login);
    }

    public Login named(LoginName login) {
        if (!names(login)) {
            throw new Refusal("The machine profile names no Claude login " + login + " under claude.logins; add it with its configDirectory.");
        }
        return new Login(login, configDirectories.get(login));
    }
}
