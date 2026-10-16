package io.github.ythibos.edi.adapter.git;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramRun;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.OutgoingCommit;
import io.github.ythibos.edi.domain.publish.RevisionRange;

@Component
public class Git {

    private static final String NUL = "\0";
    private static final String IDENTITIES_AND_MESSAGE = "--format=%an <%ae>%x00%cn <%ce>%x00%B";

    private final ExternalPrograms programs;

    public Git(ExternalPrograms programs) {
        this.programs = programs;
    }

    public Path topLevel(Path repository) {
        return Path.of(git(repository, "rev-parse", "--show-toplevel").strip());
    }

    public Path gitDirectory(Path repository) {
        return Path.of(git(repository, "rev-parse", "--absolute-git-dir").strip());
    }

    public Path commonDirectory(Path repository) {
        return Path.of(git(repository, "rev-parse", "--path-format=absolute", "--git-common-dir").strip());
    }

    public boolean isRepository(Path directory) {
        return directory.toFile().isDirectory() && run(directory, "rev-parse", "--is-inside-work-tree").succeeded();
    }

    public void fetchOrigin(Path clone) {
        git(clone, "fetch", "--quiet", "origin");
    }

    public boolean hasRef(Path repository, String ref) {
        return run(repository, "rev-parse", "--verify", "--quiet", ref + "^{commit}").succeeded();
    }

    public void addWorktreeOnNewBranch(Path clone, Path worktree, BranchName branch, String startPoint) {
        git(clone, "worktree", "add", "--quiet", "-b", branch.toString(), worktree.toString(), startPoint);
    }

    public void addWorktreeOnBranch(Path clone, Path worktree, BranchName branch) {
        git(clone, "worktree", "add", "--quiet", worktree.toString(), branch.toString());
    }

    public String currentBranch(Path worktree) {
        return git(worktree, "symbolic-ref", "--quiet", "--short", "HEAD").strip();
    }

    public void enableWorktreeConfiguration(Path clone) {
        git(clone, "config", "extensions.worktreeConfig", "true");
    }

    public void configureWorktree(Path worktree, String key, String value) {
        git(worktree, "config", "--worktree", key, value);
    }

    public Set<String> changedFiles(Path worktree, String base) {
        Set<String> changed = new TreeSet<>(nulSeparated(git(worktree, "diff", "--name-only", "-z", "--no-renames", base + "...HEAD", "--")));
        changed.addAll(nulSeparated(git(worktree, "ls-files", "-z", "--modified", "--others", "--deleted", "--exclude-standard")));
        return changed;
    }

    public String headSummary(Path worktree) {
        return git(worktree, "log", "-1", "--format=%h %s").strip();
    }

    public int commitsOnNoRemote(Path worktree) {
        return Integer.parseInt(git(worktree, "rev-list", "--count", "HEAD", "--not", "--remotes").strip());
    }

    public boolean hasUncommittedChanges(Path worktree) {
        return !git(worktree, "status", "--porcelain", "--untracked-files=normal").isBlank();
    }

    public void fetchBranchInto(Path checkout, Path worktree, BranchName branch) {
        git(checkout, "fetch", "--quiet", worktree.toString(), branch + ":" + branch);
    }

    public OutgoingChanges outgoingChanges(Path repository, RevisionRange outgoing) {
        return new OutgoingChanges(commitIds(repository, outgoing).stream()
                .map(id -> commit(repository, id))
                .toList());
    }

    private List<String> commitIds(Path repository, RevisionRange outgoing) {
        return git(repository, "rev-list", "--reverse", "--no-merges", outgoing.expression(), "--").lines().toList();
    }

    private OutgoingCommit commit(Path repository, String id) {
        String[] identitiesAndMessage = git(repository, "show", "-s", IDENTITIES_AND_MESSAGE, id).split(NUL, 3);
        return new OutgoingCommit(
                id,
                identitiesAndMessage[0],
                identitiesAndMessage[1],
                identitiesAndMessage[2].strip(),
                paths(repository, id),
                UnifiedDiff.addedLines(patch(repository, id)));
    }

    private List<String> paths(Path repository, String id) {
        return Stream.of(git(repository, "diff-tree", "-r", "--root", "--no-commit-id", "--name-only", "--diff-filter=d", "-z", id).split(NUL))
                .filter(path -> !path.isEmpty())
                .toList();
    }

    private String patch(Path repository, String id) {
        return git(repository, "diff-tree", "-r", "--root", "--no-commit-id", "--patch", "--unified=0",
                "--no-color", "--no-ext-diff", "--no-textconv", "--no-renames", "--src-prefix=a/", "--dst-prefix=b/", id);
    }

    private static Set<String> nulSeparated(String output) {
        return Stream.of(output.split(NUL)).filter(path -> !path.isEmpty()).collect(Collectors.toSet());
    }

    private String git(Path repository, String... arguments) {
        return run(repository, arguments).outputOfSuccess();
    }

    private ProgramRun run(Path repository, String... arguments) {
        List<String> commandLine = Stream.concat(Stream.of("-c", "core.quotePath=false", "-c", "color.ui=never"), Stream.of(arguments))
                .toList();
        return programs.run(Program.GIT, repository, commandLine);
    }
}
