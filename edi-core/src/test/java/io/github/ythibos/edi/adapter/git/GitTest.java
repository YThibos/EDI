package io.github.ythibos.edi.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.adapter.process.ExternalPrograms;
import io.github.ythibos.edi.domain.publish.AddedLine;
import io.github.ythibos.edi.domain.publish.OutgoingChanges;
import io.github.ythibos.edi.domain.publish.OutgoingCommit;
import io.github.ythibos.edi.domain.publish.RevisionRange;
import io.github.ythibos.edi.testing.FixtureRepository;

@Tag("adapter")
class GitTest {

    @TempDir
    private Path directory;

    private final Git git = new Git(new ExternalPrograms());
    private FixtureRepository repository;

    @BeforeEach
    void createRepository() throws IOException {
        repository = FixtureRepository.initialise(directory)
                .write("notes.txt", "first\nsecond\nthird\n")
                .commit("Start the notes");
    }

    @Test
    void readsOnlyTheOutgoingCommitsOldestFirst() throws IOException {
        repository.branch("work")
                .write("a.txt", "a\n").commit("Add a")
                .write("b.txt", "b\n").commit("Add b");

        OutgoingChanges changes = git.outgoingChanges(repository.root(), new RevisionRange("main..work"));

        assertThat(changes.commits()).extracting(OutgoingCommit::message).containsExactly("Add a", "Add b");
    }

    @Test
    void readsTheIdentitiesAndTheWholeMessageOfACommit() throws IOException {
        repository.branch("work")
                .write("a.txt", "a\n")
                .commitAs("Ada Lovelace <ada@example.org>", "Add a\n\nWith a body line.");

        OutgoingCommit commit = onlyOutgoingCommit();

        assertThat(commit.id()).isEqualTo(repository.head());
        assertThat(commit.author()).isEqualTo("Ada Lovelace <ada@example.org>");
        assertThat(commit.committer()).isEqualTo("Ada Lovelace <ada@example.org>");
        assertThat(commit.message()).isEqualTo("Add a\n\nWith a body line.");
    }

    @Test
    void readsTheAddedLinesWithTheirNumbersInTheNewVersion() throws IOException {
        repository.branch("work")
                .write("notes.txt", "first\nsecond and a half\nthird\nfourth\n")
                .write("dir with space/new.txt", "only\n")
                .commit("Edit the notes");

        OutgoingCommit commit = onlyOutgoingCommit();

        assertThat(commit.paths()).containsExactlyInAnyOrder("notes.txt", "dir with space/new.txt");
        assertThat(commit.addedLines()).containsExactlyInAnyOrder(
                new AddedLine("notes.txt", 2, "second and a half"),
                new AddedLine("notes.txt", 4, "fourth"),
                new AddedLine("dir with space/new.txt", 1, "only"));
    }

    @Test
    void listsADeletedFileAsAPathWithoutAddedLines() throws IOException {
        repository.branch("work")
                .write("kept.txt", "kept\n").commit("Keep a file");
        Files.delete(repository.root().resolve("notes.txt"));
        repository.commit("Remove the notes");

        OutgoingChanges changes = git.outgoingChanges(repository.root(), new RevisionRange("HEAD~1..HEAD"));

        assertThat(changes.commits()).singleElement().satisfies(commit -> {
            assertThat(commit.paths()).containsExactly("notes.txt");
            assertThat(commit.addedLines()).isEmpty();
        });
    }

    @Test
    void findsTheTopLevelOfTheRepositoryFromASubdirectory() throws IOException {
        repository.write("deep/er/file.txt", "x\n").commit("Nest a file");

        assertThat(git.topLevel(repository.root().resolve("deep/er"))).isEqualTo(repository.root().toRealPath());
    }

    private OutgoingCommit onlyOutgoingCommit() {
        OutgoingChanges changes = git.outgoingChanges(repository.root(), new RevisionRange("main..work"));
        assertThat(changes.commits()).hasSize(1);
        return changes.commits().getFirst();
    }
}
