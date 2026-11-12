package io.github.ythibos.edi.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.domain.authority.CallingSession;
import io.github.ythibos.edi.domain.decision.TaskReference;
import io.github.ythibos.edi.domain.dispatch.CategoryId;
import io.github.ythibos.edi.domain.event.Actor;
import io.github.ythibos.edi.testing.FixtureRepository;
import io.github.ythibos.edi.testing.Patience;
import io.github.ythibos.edi.testing.RuleCatalogue;
import io.github.ythibos.edi.testing.StandInScripts;
import io.github.ythibos.edi.testing.TestHome;
import io.github.ythibos.edi.testing.WorkerFixtures;

@Tag("adapter")
class CommitMsgHookFiringsTest {

    private static final String RECORDING_EDI = "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$EDI_CALLS\"\n";

    @TempDir
    private Path directory;

    @Test
    void recordsEachCommitItRefusesThroughEdiUnderTheRuleTheCatalogueNamesIt() throws IOException, InterruptedException {
        Path hook = commitMsgHookOfADispatchedWorker();
        Path calls = directory.resolve("edi-calls");
        Path stubs = StandInScripts.installed(directory.resolve("stubs/edi"), RECORDING_EDI).getParent();

        List<Integer> exitCodes = List.of(
                run(hook, "Fit the lamp\n", stubs, calls),
                run(hook, "HRN-3 Fit the lamp\n\nCo-authored-by: A Person <person@example.org>\n", stubs, calls),
                run(hook, "HRN-3 Fit the lamp\n\nSigned-off-by: Claude Opus <noreply@example.org>\n", stubs, calls),
                run(hook, "HRN-3 Fit the lamp\n\nGenerated with an AI assistant\n", stubs, calls),
                run(hook, "HRN-3 Fit the lamp\n", stubs, calls));

        assertThat(exitCodes).containsExactly(1, 1, 1, 1, 0);
        assertThat(Files.readAllLines(calls)).allSatisfy(call -> assertThat(call).startsWith("rules fired ").contains(" --reason "))
                .extracting(call -> call.split(" ")[2])
                .containsExactlyInAnyOrderElementsOf(RuleCatalogue.ofTheRepository().rulesFiredBy("commit-msg"));
    }

    private Path commitMsgHookOfADispatchedWorker() throws IOException {
        TestHome home = WorkerFixtures.homeWithProjects(directory);
        home.storing(List.of(WorkerFixtures.queuedShip("fit-lamp", "harness", "HRN-3")));
        DispatchOutcome.Launched launched = home.dispatchTask().dispatch(TaskReference.parse("fit-lamp"),
                new DispatchRequest(CategoryId.IMPLEMENTATION, "", List.of(), null), CallingSession.UNNAMED, Actor.PRIMARY).launched();
        return Path.of(FixtureRepository.at(launched.worktree()).run("rev-parse", "--absolute-git-dir").strip()).resolve("edi-hooks/commit-msg");
    }

    private int run(Path hook, String message, Path stubs, Path calls) throws IOException, InterruptedException {
        Path messageFile = Files.writeString(Files.createTempFile(directory, "COMMIT_EDITMSG", ""), message);
        ProcessBuilder commitMsg = new ProcessBuilder("sh", hook.toString(), messageFile.toString()).redirectErrorStream(true);
        commitMsg.environment().keySet().removeIf(variable -> variable.startsWith("EDI_"));
        commitMsg.environment().put("PATH", stubs + ":" + System.getenv("PATH"));
        commitMsg.environment().put("EDI_CALLS", calls.toString());
        Process process = commitMsg.start();
        process.getInputStream().readAllBytes();
        if (!process.waitFor(Patience.FOR_REAL_WORK.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("The commit-msg hook did not finish.");
        }
        return process.exitValue();
    }
}
