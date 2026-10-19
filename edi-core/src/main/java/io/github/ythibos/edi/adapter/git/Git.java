package io.github.ythibos.edi.adapter.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
import io.github.ythibos.edi.adapter.process.ProgramCall;
import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.process.ProgramRun;
import io.github.ythibos.edi.domain.branch.BranchName;
import io.github.ythibos.edi.domain.branch.CheckedOutBranch;
import io.github.ythibos.edi.domain.delivery.ChangedPath;
import io.github.ythibos.edi.domain.delivery.LocalChange;
import io.github.ythibos.edi.domain.landing.BranchCommit;
import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.OutgoingCommit;
import io.github.ythibos.edi.domain.publish.RevisionRange;

@Component
public class Git {

    private static final String NUL = "\0";
    private static final String IDENTITIES_AND_MESSAGE = "--format=%an <%ae>%x00%cn <%ce>%x00%B";
    private static final String BUILD_FILES = ":(glob)**/pom.xml";
    private static final String NESTED_REPOSITORY_MARK = "/";
    private static final String SAME_CHANGE_IN_THE_BASE = "-";
    private static final int CONFLICTS = 1;
    private static final int STATUS_MARKS = 3;
    private static final String LITERAL_PATH = ":(literal)";

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

    public Optional<CheckedOutBranch> checkedOutBranchAt(Path directory) {
        if (!directory.toFile().isDirectory()) {
            return Optional.empty();
        }
        ProgramRun status = run(directory, "--no-optional-locks", "status", "--porcelain=v2", "--branch", "--untracked-files=normal");
        return status.succeeded() ? Optional.of(PorcelainStatus.checkedOutBranchIn(status.output())) : Optional.empty();
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

    public String head(Path repository) {
        return git(repository, "rev-parse", "--verify", "HEAD^{commit}").strip();
    }

    public Optional<String> commitAt(Path repository, String ref) {
        ProgramRun resolved = run(repository, "rev-parse", "--verify", "--quiet", ref + "^{commit}");
        return resolved.succeeded() ? Optional.of(resolved.output().strip()) : Optional.empty();
    }

    public boolean isAncestor(Path repository, String commit, String ref) {
        return run(repository, "merge-base", "--is-ancestor", commit, ref).succeeded();
    }

    public String pushUrlOf(Path repository, String remote) {
        return git(repository, "remote", "get-url", "--push", remote).strip();
    }

    public List<AddedLine> buildFileLinesAddedSince(Path worktree, String base) {
        return UnifiedDiff.addedLines(git(worktree, "diff", "--unified=0", "--no-color", "--no-ext-diff", "--no-textconv", "--no-renames",
                "--src-prefix=a/", "--dst-prefix=b/", base + "...HEAD", "--", BUILD_FILES));
    }

    public List<String> uncommittedPaths(Path worktree) {
        return statusEntries(worktree).stream().filter(path -> !path.endsWith(NESTED_REPOSITORY_MARK)).toList();
    }

    public List<String> nestedRepositories(Path worktree) {
        return statusEntries(worktree).stream().filter(path -> path.endsWith(NESTED_REPOSITORY_MARK)).toList();
    }

    public List<BranchCommit> commitsBeyond(Path repository, String base, String head) {
        return git(repository, "cherry", "-v", base, head).lines()
                .map(line -> line.split(" ", 3))
                .map(mark -> new BranchCommit(mark[1], mark.length > 2 ? mark[2] : "", mark[0].equals(SAME_CHANGE_IN_THE_BASE)))
                .toList();
    }

    public boolean holdsTheContentOf(Path repository, String base, String head) {
        ProgramRun merged = run(repository, "merge-tree", "--write-tree", base, head);
        return merged.succeeded() && merged.output().lines().findFirst().orElse("").equals(git(repository, "rev-parse", base + "^{tree}").strip());
    }

    public LocalChange changeSince(Path worktree, String base) {
        ProgramRun merged = run(worktree, "merge-tree", "--write-tree", base, "HEAD");
        if (merged.exitCode() == CONFLICTS) {
            throw new ProgramFailure("the branch conflicts with " + base + ", so no scoped patch can be made from it.");
        }
        String tree = merged.outputOfSuccess().lines().findFirst().orElseThrow().strip();
        return new LocalChange(changedPaths(worktree, base, tree), git(worktree, "diff", "--binary", "--full-index", "--no-color", "--no-ext-diff",
                "--no-textconv", "--find-renames", "--src-prefix=a/", "--dst-prefix=b/", base, tree));
    }

    public List<String> uncommittedAmong(Path checkout, List<String> paths) {
        List<String> arguments = new ArrayList<>(List.of("status", "--porcelain=v1", "-z", "--untracked-files=all", "--no-renames", "--"));
        paths.forEach(path -> arguments.add(LITERAL_PATH + path));
        return nulSeparated(git(checkout, arguments.toArray(String[]::new))).stream().map(entry -> entry.substring(STATUS_MARKS)).sorted().toList();
    }

    public void applyToTheWorkingTree(Path checkout, String patch) {
        withInput(checkout, patch, "apply", "--check", "--whitespace=nowarn").outputOfSuccess();
        withInput(checkout, patch, "apply", "--whitespace=nowarn").outputOfSuccess();
    }

    public void removeWorktree(Path clone, Path worktree) {
        git(clone, "worktree", "remove", worktree.toString());
    }

    public void discardWorktree(Path clone, Path worktree) {
        git(clone, "worktree", "remove", "--force", worktree.toString());
    }

    public void deleteBranch(Path clone, BranchName branch) {
        git(clone, "branch", "--quiet", "-D", branch.toString());
    }

    public void bindCredentials(Path clone, String urlPrefix, String helper) {
        String key = "credential." + urlPrefix + ".helper";
        run(clone, "config", "--local", "--unset-all", key);
        git(clone, "config", "--local", "--add", key, "");
        git(clone, "config", "--local", "--add", key, helper);
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

    private List<String> statusEntries(Path worktree) {
        return nulSeparated(git(worktree, "status", "--porcelain=v1", "-z", "--untracked-files=all", "--no-renames")).stream()
                .map(entry -> entry.substring(STATUS_MARKS))
                .sorted()
                .toList();
    }

    private List<ChangedPath> changedPaths(Path worktree, String base, String tree) {
        return RawDiff.changedPaths(git(worktree, "diff", "--raw", "-z", "--no-abbrev", "--find-renames", base, tree));
    }

    private ProgramRun withInput(Path repository, String input, String... arguments) {
        return programs.run(ProgramCall.of(Program.GIT, null, repository, commandLineOf(arguments)).withInput(input));
    }

    private static Set<String> nulSeparated(String output) {
        return Stream.of(output.split(NUL)).filter(path -> !path.isEmpty()).collect(Collectors.toSet());
    }

    private String git(Path repository, String... arguments) {
        return run(repository, arguments).outputOfSuccess();
    }

    private ProgramRun run(Path repository, String... arguments) {
        return programs.run(Program.GIT, repository, commandLineOf(arguments));
    }

    private static List<String> commandLineOf(String... arguments) {
        return Stream.concat(Stream.of("-c", "core.quotePath=false", "-c", "color.ui=never"), Stream.of(arguments)).toList();
    }
}
