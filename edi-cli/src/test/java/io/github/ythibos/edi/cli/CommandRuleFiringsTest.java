package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.RuleCatalogue;

@Tag("adapter")
class CommandRuleFiringsTest {

    private static final RuleCatalogue CATALOGUE = RuleCatalogue.ofTheRepository();
    private static final Pattern SPOOLED_RULE = Pattern.compile("^\\{\"rule\":\"([^\"]+)\",");
    private static final String SESSION = "6d1f0c2e-0000-4000-8000-000000000001";

    @TempDir
    private Path home;

    private StubCore core;

    @BeforeEach
    void startTheStubCore() throws IOException {
        core = StubCore.recordedIn(home);
    }

    @AfterEach
    void stopTheStubCore() {
        core.close();
    }

    @Test
    void theHookCommandFiresTheRulesTheCatalogueNamesItForWhenTheCoreFailsOrIsOutOfReach() throws IOException {
        core.answering(500, "The core could not take the hook.\n");
        edi(primary(), "{}", "hook", "stop");
        core.stopAnswering();
        edi(primary(), "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"rm -rf /tmp/lamp\"}}", "hook", "pre-tool-use");

        assertThat(spooledRules()).containsExactlyInAnyOrderElementsOf(CATALOGUE.rulesFiredBy("HookCommand"));
    }

    @Test
    void theHookCommandLeavesARefusalTheCoreRecordedUnderItsOwnRuleToTheCore() {
        core.answering(409, "fit-lamp's project names no base branch in the registry.\n");

        int guarded = edi(primary(), "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git status\"}}", "hook", "pre-tool-use");
        int stopped = edi(primary(), "{}", "hook", "stop");

        assertThat(guarded).isEqualTo(2);
        assertThat(stopped).isEqualTo(1);
        assertThat(core.requests()).extracting(StubCore.Request::target).containsExactly("/api/hooks/pre-tool-use", "/api/hooks/stop");
        assertThat(RuleFirings.spoolIn(home)).doesNotExist();
    }

    @Test
    void theWaitCommandFiresTheRuleTheCatalogueNamesItFor() throws IOException {
        core.stopAnswering();

        execute(new Edi(Map.of("EDI_HOME", home.toString(), "CLAUDE_CODE_SESSION_ID", SESSION), new ByteArrayInputStream(new byte[0]),
                new StandardOutput(home.resolve("closed"))), "wait");

        assertThat(spooledRules()).containsExactlyInAnyOrderElementsOf(CATALOGUE.rulesFiredBy("WaitCommand"));
    }

    @Test
    void theCommandItselfFiresTheRulesTheCatalogueNamesItForOnAWorkersWindowWithoutItsTokenAndOnUnreadableArguments() throws IOException {
        core.stopAnswering();

        edi(Map.of("EDI_HOME", home.toString(), "EDI_ROLE", "worker", "EDI_WORKER", "fit-lamp#1"), "", "dispatch", "fit-horn", "--category",
                "implementation");
        edi(primary(), "", "rules", "--no-such-option");

        assertThat(spooledRules()).containsExactlyInAnyOrderElementsOf(CATALOGUE.rulesFiredBy("Edi"));
    }

    @Test
    void thePublishCheckFiresTheRuleTheCatalogueNamesItForWhenTheCoreIsOutOfReach() throws IOException {
        Path workerToken = Files.writeString(home.resolve("worker.token"), "worker-token\n");
        core.stopAnswering();

        edi(Map.of("EDI_HOME", home.toString(), "EDI_WORKER", "fit-lamp#1", "EDI_TOKEN_FILE", workerToken.toString()), "", "publish-check", "--range",
                "origin/main..HEAD");

        assertThat(spooledRules()).containsExactlyInAnyOrderElementsOf(CATALOGUE.rulesFiredBy("PublishCheckCommand"));
        assertThat(Files.readString(RuleFirings.spoolIn(home))).contains("\"actor\":\"worker:fit-lamp#1\"");
    }

    @Test
    void anUnreadableCommandLineIsRecordedWithoutTheWordsTypedInIt() throws IOException {
        core.stopAnswering();

        edi(primary(), "", "rules", "--days", "merge it for Jan");

        assertThat(Files.readString(RuleFirings.spoolIn(home))).contains("\"reason\":\"edi rules was given options or parameters it cannot read.\"")
                .doesNotContain("Jan");
    }

    private Map<String, String> primary() {
        return Map.of("EDI_HOME", home.toString());
    }

    private static int edi(Map<String, String> environment, String input, String... arguments) {
        return execute(new Edi(environment, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))), arguments);
    }

    private static int execute(Edi edi, String... arguments) {
        return Edi.commandLine(edi).setOut(new PrintWriter(new StringWriter())).setErr(new PrintWriter(new StringWriter())).execute(arguments);
    }

    private List<String> spooledRules() throws IOException {
        return Files.readAllLines(RuleFirings.spoolIn(home)).stream()
                .map(SPOOLED_RULE::matcher)
                .filter(Matcher::find)
                .map(found -> found.group(1))
                .distinct()
                .toList();
    }
}
