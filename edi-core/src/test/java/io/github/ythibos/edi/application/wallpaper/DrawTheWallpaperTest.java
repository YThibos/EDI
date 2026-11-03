package io.github.ythibos.edi.application.wallpaper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.testing.TestHome;

class DrawTheWallpaperTest {

    @TempDir
    private Path directory;

    @Test
    void drawsTheWallpaperAgainOnceTheCaptainChangesTheBlink() throws IOException {
        TestHome home = TestHome.in(directory).writing("config/personal.yaml", "wallpaper:\n  blinkEvery: PT20S\n");
        DrawTheWallpaper drawing = home.drawTheWallpaper();

        drawing.draw();
        byte[] everyTwentySeconds = Files.readAllBytes(home.home().wallpaper());
        home.writing("config/personal.yaml", "wallpaper:\n  blinkEvery: PT45S\n");
        drawing.draw();

        assertThat(Files.readAllBytes(home.home().wallpaper())).isNotEqualTo(everyTwentySeconds);
    }

    @Test
    void keepsTheLastDrawingWhileThePersonalConfigurationIsRefused() throws IOException {
        TestHome home = TestHome.in(directory).writing("config/personal.yaml", "wallpaper:\n  blinkEvery: PT20S\n");
        DrawTheWallpaper drawing = home.drawTheWallpaper();
        drawing.draw();
        byte[] drawn = Files.readAllBytes(home.home().wallpaper());

        home.writing("config/personal.yaml", "wallpaper:\n  blinkEvery: whenever\n");
        drawing.draw();

        assertThat(Files.readAllBytes(home.home().wallpaper())).isEqualTo(drawn);
    }
}
