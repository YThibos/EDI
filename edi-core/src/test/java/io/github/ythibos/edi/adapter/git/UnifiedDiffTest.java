package io.github.ythibos.edi.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.publish.AddedLine;

class UnifiedDiffTest {

    @Test
    void numbersAddedLinesFromEachHunkOfEachFile() {
        String patch = """
                diff --git a/notes.txt b/notes.txt
                index 1111111..2222222 100644
                --- a/notes.txt
                +++ b/notes.txt
                @@ -2 +2 @@
                -second
                +second and a half
                @@ -3,0 +4,2 @@
                +fourth
                +fifth
                diff --git a/new.txt b/new.txt
                new file mode 100644
                --- /dev/null
                +++ b/new.txt
                @@ -0,0 +1 @@
                +only
                """;

        assertThat(UnifiedDiff.addedLines(patch)).containsExactly(
                new AddedLine("notes.txt", 2, "second and a half"),
                new AddedLine("notes.txt", 4, "fourth"),
                new AddedLine("notes.txt", 5, "fifth"),
                new AddedLine("new.txt", 1, "only"));
    }

    @Test
    void readsAPathWithSpacesWithoutTheTabGitEndsItsHeaderWith() {
        String patch = """
                diff --git a/dir with space/new.txt b/dir with space/new.txt
                --- /dev/null
                +++ b/dir with space/new.txt\t
                @@ -0,0 +1 @@
                +only
                """;

        assertThat(UnifiedDiff.addedLines(patch)).containsExactly(new AddedLine("dir with space/new.txt", 1, "only"));
    }

    @Test
    void readsAnAddedLineThatLooksLikeAFileHeader() {
        String patch = """
                diff --git a/list.md b/list.md
                --- a/list.md
                +++ b/list.md
                @@ -1,0 +2 @@
                +++ not a header
                \\ No newline at end of file
                """;

        assertThat(UnifiedDiff.addedLines(patch)).containsExactly(new AddedLine("list.md", 2, "++ not a header"));
    }

    @Test
    void countsContextLinesWhenThePatchCarriesThem() {
        String patch = """
                diff --git a/notes.txt b/notes.txt
                --- a/notes.txt
                +++ b/notes.txt
                @@ -1,2 +1,3 @@
                 first
                +inserted
                 second
                """;

        assertThat(UnifiedDiff.addedLines(patch)).containsExactly(new AddedLine("notes.txt", 2, "inserted"));
    }

    @Test
    void readsNothingFromADeletedFile() {
        String patch = """
                diff --git a/gone.txt b/gone.txt
                deleted file mode 100644
                --- a/gone.txt
                +++ /dev/null
                @@ -1 +0,0 @@
                -gone
                """;

        assertThat(UnifiedDiff.addedLines(patch)).isEmpty();
    }
}
