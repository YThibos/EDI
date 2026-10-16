package io.github.ythibos.edi.application.dispatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.task.BaseBranch;

@Component
@ImportRuntimeHints(WorktreeHooks.HookTemplates.class)
public class WorktreeHooks {

    private static final String HOOKS_DIRECTORY = "edi-hooks";
    private static final String COMMIT_MSG = "commit-msg";
    private static final String PRE_PUSH = "pre-push";
    private static final String TEMPLATES = "git-hooks/";

    private final Git git;

    public WorktreeHooks(Git git) {
        this.git = git;
    }

    public void installIn(ProjectEntry project, Path worktree, BranchName branch, BaseBranch base) {
        Path hooks = git.gitDirectory(worktree).resolve(HOOKS_DIRECTORY);
        write(hooks.resolve(COMMIT_MSG), template(COMMIT_MSG).replace("@WORK_KEY@", branch.workKey().toString()));
        write(hooks.resolve(PRE_PUSH), template(PRE_PUSH).replace("@BASE_REF@", "origin/" + base));
        git.enableWorktreeConfiguration(project.projectClone());
        git.configureWorktree(worktree, "core.hooksPath", hooks.toString());
    }

    private static String template(String hook) {
        try (InputStream content = WorktreeHooks.class.getClassLoader().getResourceAsStream(TEMPLATES + hook)) {
            if (content == null) {
                throw new IllegalStateException(TEMPLATES + hook + " is missing from the core's resources.");
            }
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void write(Path hook, String script) {
        try {
            Files.createDirectories(hook.getParent());
            Files.writeString(hook, script, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    static final class HookTemplates implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            List.of(COMMIT_MSG, PRE_PUSH).forEach(hook -> hints.resources().registerPattern(TEMPLATES + hook));
        }
    }
}
