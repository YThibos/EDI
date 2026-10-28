package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PrimaryWindowTest {

    private static final PrimaryLaunch LAUNCH = new PrimaryLaunch("edi-261028-0737", Path.of("/edi/home"), Path.of("/edi/home/settings.json"),
            Path.of("/edi/home/anchor.md"));

    @Test
    void opensTheSessionAttachedAndTitlesTheTerminalWithItsName() {
        PrimaryWindow outsideTmux = new PrimaryWindow(Map.of());

        assertThat(outsideTmux.newSession(LAUNCH)).containsExactly("tmux", "new-session", "-s", "edi-261028-0737", "-c", "/edi/home",
                "claude", "--settings", "/edi/home/settings.json", "--append-system-prompt-file", "/edi/home/anchor.md",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles", "on",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles-string", "#S");
    }

    @Test
    void opensTheSessionDetachedInsideTmuxAndStillTitlesTheTerminalWithItsName() {
        PrimaryWindow insideTmux = new PrimaryWindow(Map.of("TMUX", "/tmp/tmux-1000/default,1,0"));

        assertThat(insideTmux.newSession(LAUNCH)).containsExactly("tmux", "new-session", "-d", "-s", "edi-261028-0737", "-c", "/edi/home",
                "claude", "--settings", "/edi/home/settings.json", "--append-system-prompt-file", "/edi/home/anchor.md",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles", "on",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles-string", "#S");
    }
}
