package io.github.ythibos.edi.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

record EdiCheckout(Path root) {

    void requireACleanClone() {
        if (!Files.isDirectory(root.resolve(".git"))) {
            throw new CommandFailure(root + " is not a clone of the EDI repository; EDI_CHECKOUT names the one to update from.");
        }
        String changes = git("status", "--porcelain").strip();
        if (!changes.isEmpty()) {
            throw new CommandFailure(root + " has local changes, so no update runs over them:\n" + changes);
        }
    }

    void pullTheLatest() {
        git("pull", "--ff-only", "--quiet");
    }

    void buildTestAndInstall(Path home) {
        int installed = LocalPrograms.runAttached(List.of(root.resolve("ops").resolve("install.sh").toString()), root,
                Map.of("EDI_HOME", home.toString()));
        if (installed != 0) {
            throw new CommandFailure("ops/install.sh failed with " + installed + ", so the installed build is unchanged and the core keeps running.");
        }
    }

    String head() {
        return git("log", "-1", "--format=%h %s").strip();
    }

    Path primaryAnchor() {
        return root.resolve("claude").resolve("primary").resolve("CLAUDE.md");
    }

    private String git(String... arguments) {
        return LocalPrograms.outputOf(Stream.concat(Stream.of("git", "-C", root.toString()), Arrays.stream(arguments)).toList(), root);
    }
}
