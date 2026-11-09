package io.github.ythibos.edi.application.dispatch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.git.Git;
import io.github.ythibos.edi.adapter.registry.ProjectEntry;
import io.github.ythibos.edi.application.delivery.Forges;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.delivery.RegisteredForge;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.task.BaseBranch;
import io.github.ythibos.edi.domain.task.TaskId;

@Component
public class IsolatedCopies {

    private static final String ORIGIN = "origin";

    private final EdiHome home;
    private final Git git;
    private final WorktreeHooks hooks;
    private final Forges forges;

    public IsolatedCopies(EdiHome home, Git git, WorktreeHooks hooks, Forges forges) {
        this.home = home;
        this.git = git;
        this.hooks = hooks;
        this.forges = forges;
    }

    public Path prepare(ProjectEntry project, TaskId task, BranchName branch, BaseBranch base) {
        Path worktree = home.worktreeOf(project.project().name(), task);
        requireAClone(project);
        if (Files.isDirectory(worktree)) {
            requireIsolated(worktree, project);
            requireOnItsBranch(worktree, branch);
        } else {
            requireInsideTheWorktrees(worktree.getParent());
            create(project, worktree, branch, base);
        }
        hooks.installIn(project, worktree, branch, base);
        bindTheForgeAccount(project);
        return worktree;
    }

    public void requireIsolated(Path worktree, ProjectEntry project) {
        Path copy = realPathOf(worktree);
        requireInsideTheWorktrees(worktree);
        if (copy.equals(realPathOf(project.projectClone())) || !git.isRepository(copy) || isAPrimaryCheckout(copy)) {
            throw new Refusal(Rule.ISOLATED_COPY, worktree + " is the primary directory of a clone, not an isolated copy, so no worker is launched there.");
        }
        if (!realPathOf(git.commonDirectory(copy)).equals(realPathOf(git.gitDirectory(project.projectClone())))) {
            throw new Refusal(Rule.ISOLATED_COPY, worktree + " is not an isolated copy of " + project.project().name() + "'s clone.");
        }
    }

    public void requireAClone(ProjectEntry project) {
        if (!git.isRepository(project.projectClone())) {
            throw new Refusal(Rule.ISOLATED_COPY, project.project().name() + " has no clone at " + project.projectClone() + "; clone it there first.");
        }
    }

    public Optional<String> forgeRemoteOf(ProjectEntry project) {
        RegisteredForge forge = project.forge();
        return git.fetchUrlOf(project.projectClone(), ORIGIN).filter(remote -> forge != null && forge.isServedAt(remote));
    }

    private void requireInsideTheWorktrees(Path path) {
        if (!realPathOf(path).startsWith(realPathOf(home.worktrees()))) {
            throw new Refusal(Rule.ISOLATED_COPY, path + " is outside " + home.worktrees() + ", so no worker is launched there.");
        }
    }

    private void create(ProjectEntry project, Path worktree, BranchName branch, BaseBranch base) {
        Path clone = project.projectClone();
        git.fetchOrigin(clone);
        createParentOf(worktree);
        if (git.hasRef(clone, "refs/heads/" + branch)) {
            git.addWorktreeOnBranch(clone, worktree, branch);
        } else {
            String startPoint = ORIGIN + "/" + base;
            if (!git.hasRef(clone, startPoint)) {
                throw new Refusal(Rule.ISOLATED_COPY, startPoint + " does not exist in " + clone + " after a fetch, so the isolated copy has nothing to start from.");
            }
            git.addWorktreeOnNewBranch(clone, worktree, branch, startPoint);
        }
        requireIsolated(worktree, project);
    }

    private void bindTheForgeAccount(ProjectEntry project) {
        RegisteredForge forge = project.forge();
        if (forge != null) {
            forges.serving(forge).credentialHelperFor(forge)
                    .ifPresent(helper -> git.bindCredentials(project.projectClone(), "https://" + forge.repository().host(), helper));
        }
    }

    private void requireOnItsBranch(Path worktree, BranchName branch) {
        String current = git.currentBranch(worktree);
        if (!current.equals(branch.toString())) {
            throw new Refusal(Rule.ISOLATED_COPY, worktree + " has " + current + " checked out, not " + branch + ".");
        }
    }

    private boolean isAPrimaryCheckout(Path copy) {
        return git.gitDirectory(copy).equals(git.commonDirectory(copy));
    }

    private static void createParentOf(Path worktree) {
        try {
            Files.createDirectories(worktree.getParent());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static Path realPathOf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException absent) {
            return path.toAbsolutePath().normalize();
        }
    }
}
