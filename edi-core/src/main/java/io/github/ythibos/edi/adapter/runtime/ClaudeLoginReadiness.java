package io.github.ythibos.edi.adapter.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.Refusal;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

final class ClaudeLoginReadiness {

    private static final String FIRST_RUN_FINISHED = "hasCompletedOnboarding";
    private static final List<String> AUTH_STATUS = List.of("auth", "status");
    private static final int LOGGED_IN = 0;

    private final ExternalPrograms programs;
    private final String claudeCode;
    private final ClaudeLoginFiles logins;
    private final JsonMapper json;
    private final Set<String> inheritedVariables;

    ClaudeLoginReadiness(ExternalPrograms programs, String claudeCode, ClaudeLoginFiles logins, JsonMapper json, Set<String> inheritedVariables) {
        this.programs = programs;
        this.claudeCode = claudeCode;
        this.logins = logins;
        this.json = json;
        this.inheritedVariables = Set.copyOf(inheritedVariables);
    }

    void requireReady(Login login) {
        Path configuration = logins.globalConfigurationOf(login);
        if (!Files.isRegularFile(configuration)) {
            throw notReady(login, "its global configuration " + configuration + " does not exist", firstRunOf(login));
        }
        if (!recordsTheFirstRunAsFinished(configuration)) {
            throw notReady(login, "its global configuration " + configuration + " does not record its first run as finished", firstRunOf(login));
        }
        if (!isLoggedIn(login)) {
            throw notReady(login, "claude auth status says it is not logged in", onTheLogin(login, "claude auth login"));
        }
    }

    private static Refusal notReady(Login login, String lack, String fix) {
        return new Refusal("The Claude login " + login.name() + " is not ready for a worker: " + lack + "; run " + fix + ", then try again.");
    }

    private static String firstRunOf(Login login) {
        return onTheLogin(login, "claude") + " once and finish its first run";
    }

    private static String onTheLogin(Login login, String command) {
        return login.keepsClaudeCodesOwnDirectory() ? "env -u " + ClaudeCommandLine.CONFIG_DIRECTORY + " " + command
                : ClaudeCommandLine.CONFIG_DIRECTORY + "=" + login.configDirectory() + " " + command;
    }

    private boolean recordsTheFirstRunAsFinished(Path configuration) {
        try {
            return json.readTree(Files.readString(configuration, StandardCharsets.UTF_8)).path(FIRST_RUN_FINISHED).asBoolean(false);
        } catch (JacksonException unreadable) {
            return false;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private boolean isLoggedIn(Login login) {
        return programs.exitValueOf(authStatusOn(login)) == LOGGED_IN;
    }

    private ProgramCall authStatusOn(Login login) {
        ProgramCall authStatus = ProgramCall.of(Program.CLAUDE_CODE, claudeCode, Path.of(System.getProperty("java.io.tmpdir")), AUTH_STATUS)
                .withholding(ClaudeCommandLine.withheldFromClaudeCode(login, inheritedVariables));
        return login.keepsClaudeCodesOwnDirectory() ? authStatus
                : authStatus.withVariables(Map.of(ClaudeCommandLine.CONFIG_DIRECTORY, login.configDirectory().toString()));
    }
}
