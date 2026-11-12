package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.LoginName;

public record ClaudeLogins(Map<LoginName, Path> configDirectories) {

    public static final ClaudeLogins ONLY_THE_DEFAULT = new ClaudeLogins(Map.of());

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

    public List<Login> every() {
        return Stream.concat(Stream.of(LoginName.DEFAULT), configDirectories.keySet().stream()).distinct().map(this::named).toList();
    }
}
