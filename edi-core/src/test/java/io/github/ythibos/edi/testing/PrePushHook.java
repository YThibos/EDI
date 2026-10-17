package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class PrePushHook {

    private static final String NO_REMOTE_COMMIT = "0".repeat(40);
    private static final long PATIENCE_SECONDS = 25;
    private static final String EDI_ON_THE_JVM = """
            #!/bin/sh
            range=
            remote_url=
            while [ $# -gt 0 ]; do
                case "$1" in
                    --range) range=$2; shift 2 ;;
                    --remote-url) remote_url=$2; shift 2 ;;
                    *) shift ;;
                esac
            done
            exec '%s' --enable-preview -cp '%s' io.github.ythibos.edi.application.publish.PublishGuard "$PWD" "$range" "$EDI_HOME" "$remote_url"
            """;

    private PrePushHook() {
    }

    public static HookRun pushes(FixtureRepository worktree, Path ediHome, String branch, String remoteUrl, TestProcesses processes)
            throws IOException, InterruptedException {
        Path hook = Path.of(worktree.run("rev-parse", "--absolute-git-dir").strip()).resolve("edi-hooks/pre-push");
        ProcessBuilder push = new ProcessBuilder("sh", hook.toString(), "origin", remoteUrl).directory(worktree.root().toFile()).redirectErrorStream(true);
        push.environment().put("PATH", ediCommandIn(ediHome.resolveSibling("edi-on-the-jvm")) + ":" + System.getenv("PATH"));
        push.environment().put("EDI_HOME", ediHome.toString());
        Process process = processes.register(push.start());
        try (OutputStream refs = process.getOutputStream()) {
            refs.write(("refs/heads/" + branch + " " + worktree.head() + " refs/heads/" + branch + " " + NO_REMOTE_COMMIT + "\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(PATIENCE_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("The pre-push hook did not finish.");
        }
        return new HookRun(process.exitValue(), output);
    }

    private static Path ediCommandIn(Path directory) throws IOException {
        Path edi = Files.createDirectories(directory).resolve("edi");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        StandInScripts.installed(edi, EDI_ON_THE_JVM.formatted(java, System.getProperty("java.class.path")));
        return directory;
    }

    public record HookRun(int exitCode, String output) {

        public boolean refused() {
            return exitCode != 0;
        }
    }
}
