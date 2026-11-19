package io.github.ythibos.edi.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PrimaryWindowTest {

    private static final Path HOME = Path.of("/edi/home");
    private static final PrimaryLaunch LAUNCH = new PrimaryLaunch("edi-261028-0737", Path.of("/edi/home"), Path.of("/edi/home/settings.json"),
            Path.of("/edi/home/anchor.md"), "launch-key-of-the-primary", null);

    @Test
    void opensTheSessionAttachedWithItsHomeAndTheLaunchKeyInItsEnvironmentAndTitlesTheTerminalWithItsName() {
        PrimaryWindow outsideTmux = new PrimaryWindow(Map.of(), HOME, UpLaunch.WITH_THE_BOARD);

        assertThat(outsideTmux.newSession(LAUNCH)).containsExactly("tmux", "new-session", "-s", "edi-261028-0737", "-c", "/edi/home",
                "-e", "EDI_HOME=/edi/home", "-e", "EDI_PRIMARY_LAUNCH_KEY=launch-key-of-the-primary",
                "env", "EDI_PRIMARY_LAUNCH=edi-up",
                "claude", "--settings", "/edi/home/settings.json", "--append-system-prompt-file", "/edi/home/anchor.md",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles", "on",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles-string", "#S");
    }

    @Test
    void opensTheSessionDetachedInsideTmuxAndStillTitlesTheTerminalWithItsName() {
        PrimaryWindow insideTmux = new PrimaryWindow(Map.of("TMUX", "/tmp/tmux-1000/default,1,0"), HOME, UpLaunch.WITH_THE_BOARD);

        assertThat(insideTmux.newSession(LAUNCH)).containsExactly("tmux", "new-session", "-d", "-s", "edi-261028-0737", "-c", "/edi/home",
                "-e", "EDI_HOME=/edi/home", "-e", "EDI_PRIMARY_LAUNCH_KEY=launch-key-of-the-primary",
                "env", "EDI_PRIMARY_LAUNCH=edi-up",
                "claude", "--settings", "/edi/home/settings.json", "--append-system-prompt-file", "/edi/home/anchor.md",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles", "on",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles-string", "#S");
    }

    @Test
    void resumesAnEarlierSessionWithTheSameHomeLaunchKeySettingsAndAnchor() {
        UUID earlier = UUID.fromString("0f6c3b2a-0000-4000-8000-00000000c0de");
        PrimaryWindow outsideTmux = new PrimaryWindow(Map.of(), HOME, UpLaunch.WITH_THE_BOARD);

        assertThat(outsideTmux.newSession(LAUNCH.resuming(earlier))).containsExactly("tmux", "new-session", "-s", "edi-261028-0737", "-c", "/edi/home",
                "-e", "EDI_HOME=/edi/home", "-e", "EDI_PRIMARY_LAUNCH_KEY=launch-key-of-the-primary",
                "env", "EDI_PRIMARY_LAUNCH=edi-up",
                "claude", "--settings", "/edi/home/settings.json", "--append-system-prompt-file", "/edi/home/anchor.md",
                "--resume", "0f6c3b2a-0000-4000-8000-00000000c0de",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles", "on",
                ";", "set-option", "-t", "edi-261028-0737", "set-titles-string", "#S");
    }

    @Test
    void tellsTheSessionItsStartWasAskedToLeaveTheBoardClosed() {
        PrimaryWindow withoutTheBoard = new PrimaryWindow(Map.of(), HOME, UpLaunch.WITHOUT_THE_BOARD);

        assertThat(withoutTheBoard.newSession(LAUNCH)).contains("env", "EDI_PRIMARY_LAUNCH=edi-up-no-board");
    }
}
