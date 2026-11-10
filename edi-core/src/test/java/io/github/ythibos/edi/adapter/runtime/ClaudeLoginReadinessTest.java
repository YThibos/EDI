package io.github.ythibos.edi.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.application.port.WorkerRuntime.Login;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.worker.LoginName;
import io.github.ythibos.edi.testing.StandInScripts;
import tools.jackson.databind.json.JsonMapper;

@Tag("adapter")
class ClaudeLoginReadinessTest {

    private static final String AUTH_STATUS_CLAUDE = """
            #!/bin/sh
            [ "$1 $2" = "auth status" ] || exit 2
            printf '%%s\\n' "${CLAUDE_CONFIG_DIR-unset}" >> "%s"
            echo '{"loggedIn": "anything printed here is never read"}'
            [ -n "${CLAUDE_CODE_OAUTH_TOKEN-}" ] || [ -f "${CLAUDE_CONFIG_DIR:-%s}/stand-in-logged-in" ]
            """;

    @TempDir
    private Path directory;

    private static final String LAUNCHING_SESSIONS_TOKEN = "CLAUDE_CODE_OAUTH_TOKEN";

    private Path claudeCodesHome;
    private Path secondLogin;
    private Path claude;
    private ClaudeLoginReadiness readiness;

    @BeforeEach
    void prepareAClaudeCodeThatAnswersItsAuthStatusByAMarkerFile() throws IOException {
        claudeCodesHome = Files.createDirectories(directory.resolve("claude-home"));
        secondLogin = Files.createDirectories(directory.resolve("logins/second"));
        claude = StandInScripts.installed(directory.resolve("claude"),
                AUTH_STATUS_CLAUDE.formatted(directory.resolve("auth-status-asked"), claudeCodesHome.resolve(".claude")));
        readiness = new ClaudeLoginReadiness(new ExternalPrograms(), claude.toString(), new ClaudeLoginFiles(claudeCodesHome), new JsonMapper(),
                System.getenv().keySet());
    }

    @Test
    void refusesALoginWithoutAGlobalConfigurationAndNamesTheFirstRunThatFixesIt() {
        assertThatThrownBy(() -> readiness.requireReady(second()))
                .isInstanceOf(Refusal.class)
                .hasMessage("The Claude login second is not ready for a worker: its global configuration " + secondLogin.resolve(".claude.json")
                        + " does not exist; run CLAUDE_CONFIG_DIR=" + secondLogin + " claude once and finish its first run, then try again.");
        assertThat(directory.resolve("auth-status-asked")).doesNotExist();
    }

    @Test
    void refusesALoginWhoseFirstRunIsUnfinishedEvenWhenItIsLoggedIn() throws IOException {
        Files.writeString(secondLogin.resolve(".claude.json"), "{\"numStartups\": 1}");
        Files.createFile(secondLogin.resolve("stand-in-logged-in"));

        assertThatThrownBy(() -> readiness.requireReady(second()))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("its global configuration " + secondLogin.resolve(".claude.json") + " does not record its first run as finished;"
                        + " run CLAUDE_CONFIG_DIR=" + secondLogin + " claude once and finish its first run");
    }

    @Test
    void refusesALoginThatClaudeCodeSaysIsNotLoggedInByItsExitCodeAlone() throws IOException {
        Files.writeString(secondLogin.resolve(".claude.json"), "{\"hasCompletedOnboarding\": true}");

        assertThatThrownBy(() -> readiness.requireReady(second()))
                .isInstanceOf(Refusal.class)
                .hasMessage("The Claude login second is not ready for a worker: claude auth status says it is not logged in;"
                        + " run CLAUDE_CONFIG_DIR=" + secondLogin + " claude auth login, then try again.");
        assertThat(directory.resolve("auth-status-asked")).content().isEqualTo(secondLogin + "\n");
    }

    @Test
    void passesALoginThatIsLoggedInWithItsFirstRunFinished() throws IOException {
        Files.writeString(secondLogin.resolve(".claude.json"), "{\"hasCompletedOnboarding\": true}");
        Files.createFile(secondLogin.resolve("stand-in-logged-in"));

        assertThatCode(() -> readiness.requireReady(second())).doesNotThrowAnyException();
    }

    @Test
    void asksAboutTheDefaultLoginInClaudeCodesOwnDirectoryWithoutAnyConfigurationDirectoryOfTheCore() throws IOException {
        Files.writeString(claudeCodesHome.resolve(".claude.json"), "{\"hasCompletedOnboarding\": true}");

        assertThatThrownBy(() -> readiness.requireReady(new Login(LoginName.DEFAULT, null)))
                .isInstanceOf(Refusal.class)
                .hasMessage("The Claude login default is not ready for a worker: claude auth status says it is not logged in;"
                        + " run env -u CLAUDE_CONFIG_DIR claude auth login, then try again.");
        assertThat(directory.resolve("auth-status-asked")).content().isEqualTo("unset\n");
    }

    @Test
    void refusesALoggedOutLoginEvenWhenTheCoreInheritedALaunchingSessionsTokenTheWorkerNeverGets() throws IOException {
        Files.writeString(secondLogin.resolve(".claude.json"), "{\"hasCompletedOnboarding\": true}");
        ClaudeLoginReadiness underALaunchingSession = new ClaudeLoginReadiness(new ProgramsOfACoreThatInherited(LAUNCHING_SESSIONS_TOKEN, "stand-in-token"),
                claude.toString(), new ClaudeLoginFiles(claudeCodesHome), new JsonMapper(), Set.of(LAUNCHING_SESSIONS_TOKEN));

        assertThatThrownBy(() -> underALaunchingSession.requireReady(second()))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("claude auth status says it is not logged in");
    }

    private Login second() {
        return new Login(new LoginName("second"), secondLogin);
    }

    private static final class ProgramsOfACoreThatInherited extends ExternalPrograms {

        private final String variable;
        private final String value;

        ProgramsOfACoreThatInherited(String variable, String value) {
            this.variable = variable;
            this.value = value;
        }

        @Override
        public int exitValueOf(ProgramCall call) {
            return super.exitValueOf(call.withheldVariables().contains(variable) ? call : call.withVariables(Map.of(variable, value)));
        }
    }
}
