package io.github.ythibos.edi.adapter.git;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.adapter.process.Program;
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

    private String git(Path repository, String... arguments) {
        List<String> commandLine = Stream.concat(Stream.of("-c", "core.quotePath=false", "-c", "color.ui=never"), Stream.of(arguments))
                .toList();
        return programs.run(Program.GIT, repository, commandLine).outputOfSuccess();
    }
}
