package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.project.ProjectName;
import io.github.ythibos.edi.domain.task.TaskId;
import io.github.ythibos.edi.domain.worker.Worker;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;
import tools.jackson.databind.json.JsonMapper;

class ClaudeSettingsWritesTest {

    private static final String SWITCHED_OFF = "{\"disableAllHooks\": true}";

    @TempDir
    private Path directory;

    private TestHome home;
    private Worker worker;
    private Path worktree;

    @BeforeEach
    void serveAWorker() throws IOException {
        home = TestHome.in(directory).registering("projects:\n  - name: harness\n    base: main\n");
        worker = WorkerFixtures.servingWorkerOn(home, WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3"), CategoryId.IMPLEMENTATION);
        worktree = Files.createDirectories(home.home().worktreeOf(new ProjectName("harness"), new TaskId("fit-lamp")));
        Files.createDirectories(worktree.resolve(".claude"));
    }

    @Test
    void refusesAWorkerAnyWriteUnderItsCopysClaudeDirectory() throws IOException {
        Files.createSymbolicLink(worktree.resolve("harbour"), worktree.resolve(".claude"));
        List<ToolCall> calls = List.of(
                write("Write", ".claude/settings.local.json"),
                write("Edit", worktree.resolve(".claude/settings.json").toString()),
                write("MultiEdit", "harbour/settings.local.json"),
                shell("echo '" + SWITCHED_OFF + "' > .claude/settings.local.json"),
                shell("printf '%s' '" + SWITCHED_OFF + "' >> .claude/settings.json"),
                shell("echo '" + SWITCHED_OFF + "' | tee .claude/settings.local.json"),
                shell("cp /tmp/off.json .claude/settings.local.json"),
                shell("mv off.json " + worktree.resolve(".claude/settings.json")),
                shell("install -m 600 off.json .claude/settings.local.json"),
                shell("sed -i 's/false/true/' .claude/settings.json"),
                shell("cp off.json harbour/settings.local.json"),
                shell("cp -t .claude off.json"),
                shell("dd if=off.json of=.claude/settings.local.json"),
                shell("bash -c \"echo '{}' > .claude/settings.local.json\""),
                shell("echo '" + SWITCHED_OFF + "' > \"$PWD/.claude/settings.local.json\""),
                shell("echo '" + SWITCHED_OFF + "' > \"${PWD}/.claude/settings.json\""),
                shell("D=.claude; echo '" + SWITCHED_OFF + "' > \"$D/settings.local.json\""),
                shell("cp off.json \"$SETTINGS\""),
                shell("cp -r /tmp/lamp/.claude ."),
                shell("cp -r /tmp/lamp/. ."),
                shell("rsync -a /tmp/lamp/ ./"),
                shell("rsync -a /tmp/lamp/.claude ."),
                shell("mv /tmp/lamp/.claude ."),
                shell("git checkout -- .claude"),
                shell("git checkout lamp -- .claude/settings.json"),
                shell("git restore --source lamp .claude"),
                shell("tar -xf /tmp/lamp.tar"),
                shell("tar xzf /tmp/lamp.tgz -C ."),
                shell("tar --extract --file=/tmp/lamp.tar --directory=" + worktree),
                shell("perl -pi -e 's/false/true/' .claude/settings.json"),
                shell("perl -i.bak -pe 's/false/true/' .claude/settings.local.json"),
                shell("mv .claude /tmp/lamp-settings"),
                shell("mv " + worktree.resolve(".claude") + " old-settings"),
                shell("> .claude/settings.local.json"),
                shell(">.claude/settings.json echo '" + SWITCHED_OFF + "'"),
                shell("echo '" + SWITCHED_OFF + "' >& .claude/settings.json"),
                shell("echo '" + SWITCHED_OFF + "' 1<> .claude/settings.json"),
                shell("cp off.json .claude/settings.local.json 2>/dev/null"),
                shell("2>/dev/null cp off.json .claude/settings.local.json"),
                shell("sed -ni 's/false/true/p' .claude/settings.json"),
                shell("perl -0pi -e 's/false/true/' .claude/settings.json"),
                shell("perl -lpi -e 's/false/true/' .claude/settings.json"),
                shell("perl -0777pi -e 's/false/true/' .claude/settings.json"),
                shell("perl -dtpi -e 's/false/true/' .claude/settings.json"),
                shellFrom(".claude/skills", "echo '" + SWITCHED_OFF + "' > ../settings.local.json"),
                shellFrom(".claude", "cp /tmp/off.json settings.local.json"));

        assertThat(calls).filteredOn(call -> !refusesTheSettings(guarded(call))).isEmpty();
    }

    @Test
    void namesTheRuleAndTheWriteItJudged() {
        assertThat(guarded(shell("echo '" + SWITCHED_OFF + "' > .claude/settings.local.json")).reason())
                .startsWith("The guard refuses a write under the copy's .claude directory under the rule guard.claude-settings: ");
    }

    @Test
    void letsAWorkerReadItsClaudeDirectoryFromInsideIt() throws IOException {
        Files.createDirectories(worktree.resolve(".claude/skills/lamp"));
        for (ToolCall call : List.of(shellFrom(".claude/skills/lamp", "pwd"), shellFrom(".claude/skills/lamp", "cat SKILL.md"),
                shellFrom(".claude/skills/lamp", "ls -la"), shellFrom(".claude/skills/lamp", "cd ../../.."),
                shellFrom(".claude/skills/lamp", "grep -n lamp SKILL.md 2>&1 | head -20"),
                shellFrom(".claude/skills/lamp", "cat SKILL.md 2>/dev/null"),
                shellFrom(".claude/skills/lamp", "edi status blocked --key lamp -- the guard refused my shell"),
                shellFrom(".claude", "git status --short"))) {
            assertThat(guarded(call)).as(call.toString()).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Test
    void letsAWorkerReadItsClaudeDirectoryAndWriteItsSources() {
        for (ToolCall call : List.of(new ToolCall("Read", null, ".claude/settings.json", worktree), shell("cat .claude/settings.json"),
                shell("cp .claude/settings.json /tmp/settings-copy.json"), shell("mv .claude/notes.md docs/notes.md"), write("Write", "src/Lamp.java"),
                shell("cp -t /tmp .claude/settings.json"), shell("cp -r /tmp/lamp/src ."), shell("rsync -a /tmp/lamp/ build/"),
                shell("tar -xf /tmp/lamp.tar -C build"), shell("tar -tf /tmp/lamp.tar"), shell("git checkout -- src/Lamp.java"),
                shell("perl -pi -e 's/a/b/' src/Lamp.java"), shell("\"$JAVA_HOME/bin/java\" -version"),
                shell("echo lamp > notes.txt"),
                shell("sed -i 's/a/b/' src/Lamp.java"),
                shell("cat < .claude/settings.json"),
                shell("wc -l 0< .claude/settings.json"),
                shell("grep -c hooks <<< .claude/settings.json"),
                shell("grep -c hooks <<< \"$SETTINGS\""),
                shell("head -40 .claude/skills/lamp/SKILL.md 2>&1"),
                shell("cat .claude/skills/lamp/SKILL.md >&2"),
                shell("cat .claude/settings.json 2>&1 1>/tmp/settings-copy.json"),
                shell("perl -MList::Util=min -ne 'print' .claude/settings.json"),
                shell("perl -0777 -ne 'print' .claude/settings.json"),
                shell("perl -lne 'print if /hooks/' .claude/settings.json"),
                shell("sed -e 's/hooks/HOOKS/' .claude/settings.json"),
                shell("sed -n '1,20p' .claude/skills/lamp/SKILL.md"),
                shell(".claude/hooks/check.sh"))) {
            assertThat(guarded(call)).as(call.toString()).isEqualTo(HookAnswer.PROCEED);
        }
    }

    @Test
    void pinsTheHooksOnInTheSettingsEdiPassesAtLaunch() throws IOException {
        for (String settings : List.of("claude/worker/settings.json", "claude/primary/settings.json")) {
            try (InputStream content = ClaudeSettingsWritesTest.class.getClassLoader().getResourceAsStream(settings)) {
                assertThat(new JsonMapper().readTree(content).path("disableAllHooks").isBoolean()).as(settings).isTrue();
            }
            try (InputStream content = ClaudeSettingsWritesTest.class.getClassLoader().getResourceAsStream(settings)) {
                assertThat(new JsonMapper().readTree(content).path("disableAllHooks").asBoolean(true)).as(settings).isFalse();
            }
        }
    }

    private HookAnswer guarded(ToolCall call) {
        return home.guardToolCall().checkForWorker(worker.id(), call);
    }

    private static boolean refusesTheSettings(HookAnswer answer) {
        return answer.verdict() == HookAnswer.Verdict.DENY_THE_TOOL && answer.reason().contains("switch off the hooks that guard it");
    }

    private ToolCall write(String tool, String file) {
        return new ToolCall(tool, null, file, worktree);
    }

    private ToolCall shell(String command) {
        return new ToolCall("Bash", command, null, worktree);
    }

    private ToolCall shellFrom(String directory, String command) {
        return new ToolCall("Bash", command, null, worktree.resolve(directory));
    }
}
