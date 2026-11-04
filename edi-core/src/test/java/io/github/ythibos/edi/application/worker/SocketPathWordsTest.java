package io.github.ythibos.edi.application.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class SocketPathWordsTest {

    private static final Path COPY = Path.of("/srv/harbour/worktrees/harness/fit-lamp");

    @Test
    void readsTheWorkingAndTheTmuxDirectoriesAtTheStartOfASocketPath() {
        String tmuxDirectory = Objects.requireNonNullElse(System.getenv("TMUX_TMPDIR"), "/tmp");

        assertThat(SocketPathWords.expanded("$PWD/harbour", COPY)).isEqualTo(COPY + "/harbour");
        assertThat(SocketPathWords.expanded("${PWD}", COPY)).isEqualTo(COPY.toString());
        assertThat(SocketPathWords.expanded("$TMUX_TMPDIR/tmux-1000/edi", COPY)).isEqualTo(tmuxDirectory + "/tmux-1000/edi");
        assertThat(SocketPathWords.expanded("${TMUX_TMPDIR}/tmux-1000/edi", null)).isEqualTo(tmuxDirectory + "/tmux-1000/edi");
    }

    @Test
    void leavesAnyOtherWordAsWritten() {
        assertThat(SocketPathWords.expanded("$PWDX/harbour", COPY)).isEqualTo("$PWDX/harbour");
        assertThat(SocketPathWords.expanded("lamp/$PWD", COPY)).isEqualTo("lamp/$PWD");
        assertThat(SocketPathWords.expanded("$PWD/harbour", null)).isEqualTo("$PWD/harbour");
    }
}
