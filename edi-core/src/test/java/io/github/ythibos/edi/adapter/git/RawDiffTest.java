package io.github.ythibos.edi.adapter.git;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.ythibos.edi.domain.delivery.ChangedPath;
import io.github.ythibos.edi.domain.delivery.PathChange;

class RawDiffTest {

    private static final String BLOB = "1111111111111111111111111111111111111111";
    private static final String NONE = "0000000000000000000000000000000000000000";

    @Test
    void readsEveryKindOfChangeFromRawOutput() {
        String raw = String.join("\0",
                ":000000 100644 " + NONE + " " + BLOB + " A", "src/Horn.java",
                ":100644 100644 " + BLOB + " " + BLOB + " M", "src/Lamp.java",
                ":100644 000000 " + BLOB + " " + NONE + " D", "src/Bell.java",
                ":100644 100644 " + BLOB + " " + BLOB + " R087", "docs/old name.md", "docs/new name.md",
                ":100644 100755 " + BLOB + " " + BLOB + " M", "run.sh",
                ":120000 100644 " + BLOB + " " + BLOB + " T", "link",
                ":100644 100644 " + BLOB + " " + BLOB + " C100", "src/Lamp.java", "src/Lantern.java") + "\0";

        assertThat(RawDiff.changedPaths(raw)).containsExactly(
                ChangedPath.of("src/Horn.java", PathChange.ADDED),
                ChangedPath.of("src/Lamp.java", PathChange.MODIFIED),
                ChangedPath.of("src/Bell.java", PathChange.DELETED),
                ChangedPath.renamed("docs/old name.md", "docs/new name.md"),
                ChangedPath.of("run.sh", PathChange.MODE_CHANGED),
                ChangedPath.of("link", PathChange.MODE_CHANGED),
                ChangedPath.of("src/Lantern.java", PathChange.ADDED));
    }

    @Test
    void readsNothingFromAnEmptyDiff() {
        assertThat(RawDiff.changedPaths("")).isEmpty();
    }
}
