package io.github.ythibos.edi.adapter.fork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("adapter")
class ForkHomeTest {

    @TempDir
    private Path home;

    @Test
    void refusesAHomeWithoutABacklog() {
        assertThatIllegalArgumentException().isThrownBy(() -> ForkHome.at(home)).withMessageContaining("has no data/backlog.md");
    }

    @Test
    void findsTheHighestDecisionNumberInAnyOfItsFiles() throws IOException {
        write("backlog.md", "Captain decision id: D12\n");
        write("decision-seq", "40\n");
        write("done-archive.md", "D61 settled it; D2x, xD99 and D1234567890 are not decision numbers.\n");
        write("note-archive.md", "see D45.\n");

        SeenNumber highest = ForkHome.at(home).highestDecisionNumber();

        assertThat(highest).isEqualTo(new SeenNumber(61, "data/done-archive.md"));
    }

    @Test
    void prefersTheRecordedSequenceOnATie() throws IOException {
        write("backlog.md", "D57\n");
        write("decision-seq", "57");

        assertThat(ForkHome.at(home).highestDecisionNumber()).isEqualTo(new SeenNumber(57, "data/decision-seq"));
    }

    @Test
    void startsFromNothingWhenNothingIsNumbered() throws IOException {
        write("backlog.md", "# Backlog\n");

        assertThat(ForkHome.at(home).highestDecisionNumber().value()).isZero();
    }

    private void write(String file, String content) throws IOException {
        Files.createDirectories(home.resolve("data"));
        Files.writeString(home.resolve("data").resolve(file), content);
    }
}
