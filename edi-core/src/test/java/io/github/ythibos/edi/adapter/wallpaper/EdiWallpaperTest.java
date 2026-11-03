package io.github.ythibos.edi.adapter.wallpaper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.wallpaper.Blink;
import io.github.ythibos.edi.domain.wallpaper.Eyes;

@Tag("adapter")
class EdiWallpaperTest {

    @TempDir
    private Path home;

    @Test
    void drawsTheFaceThatBlinksEveryTheCaptainsInterval() throws IOException {
        new EdiWallpaper(new EdiHome(home)).draw(new Blink(Duration.ofSeconds(20)));

        byte[] animation = Files.readAllBytes(new EdiHome(home).wallpaper());
        BufferedImage face = ImageIO.read(new ByteArrayInputStream(animation));
        assertThat(face.getWidth()).isEqualTo(1920);
        assertThat(face.getHeight()).isEqualTo(1072);
        assertThat(frameControls(animation)).containsExactly(
                new FrameControl(1920, 1072, 0, 0, 1983),
                new FrameControl(418, 96, 761, 390, 4),
                new FrameControl(418, 96, 761, 390, 8),
                new FrameControl(418, 96, 761, 390, 5));
        assertThat(sequenceNumbers(animation)).isSorted().doesNotHaveDuplicates().startsWith(0)
                .satisfies(numbers -> assertThat(numbers.getLast()).isEqualTo(numbers.size() - 1));
        assertThat(playsForever(animation)).isTrue();
    }

    @Test
    void drawsAgainOnlyWhenTheBlinkChangesOrTheFileIsGone() throws IOException {
        EdiWallpaper wallpaper = new EdiWallpaper(new EdiHome(home));

        assertThat(wallpaper.draw(Blink.SETTLED)).isTrue();
        assertThat(wallpaper.draw(Blink.SETTLED)).isFalse();
        Files.delete(new EdiHome(home).wallpaper());
        assertThat(wallpaper.draw(Blink.SETTLED)).isTrue();
        assertThat(wallpaper.draw(new Blink(Duration.ofSeconds(30)))).isTrue();
        assertThat(frameControls(Files.readAllBytes(new EdiHome(home).wallpaper())).getFirst().delayCentiseconds()).isEqualTo(2983);
    }

    @Test
    void refusesFramesThatDoNotFitTheFirst() {
        WallpaperArt art = new WallpaperArt();
        AnimatedPng.Still eyes = new AnimatedPng.Still(art.of(Eyes.CLOSED), Duration.ofMillis(80));
        AnimatedPng.Still face = new AnimatedPng.Still(art.of(Eyes.OPEN), Duration.ofSeconds(1));

        assertThatThrownBy(() -> AnimatedPng.looping(List.of(eyes, face))).hasMessage("An animation's first frame covers the whole canvas.");
        assertThatThrownBy(() -> AnimatedPng.looping(List.of(face, new AnimatedPng.Still(art.of(Eyes.CLOSED), Duration.ofMillis(5)))))
                .hasMessage("A frame is shown for between 10 milliseconds and 655 seconds.");
    }

    private static List<FrameControl> frameControls(byte[] animation) {
        return chunksOf(animation, "fcTL").stream().map(control -> new FrameControl(control.getInt(4), control.getInt(8), control.getInt(12),
                control.getInt(16), Short.toUnsignedInt(control.getShort(20)))).toList();
    }

    private static List<Integer> sequenceNumbers(byte[] animation) {
        List<Integer> numbers = new ArrayList<>();
        ByteBuffer content = ByteBuffer.wrap(animation, 8, animation.length - 8);
        while (content.hasRemaining()) {
            int length = content.getInt();
            String type = typeOf(content);
            if (type.equals("fcTL") || type.equals("fdAT")) {
                numbers.add(content.getInt(content.position()));
            }
            content.position(content.position() + length + 4);
        }
        return numbers;
    }

    private static List<ByteBuffer> chunksOf(byte[] animation, String wanted) {
        List<ByteBuffer> chunks = new ArrayList<>();
        ByteBuffer content = ByteBuffer.wrap(animation, 8, animation.length - 8);
        while (content.hasRemaining()) {
            int length = content.getInt();
            if (typeOf(content).equals(wanted)) {
                chunks.add(content.slice(content.position(), length));
            }
            content.position(content.position() + length + 4);
        }
        return chunks;
    }

    private static boolean playsForever(byte[] animation) {
        ByteBuffer control = chunksOf(animation, "acTL").getFirst();
        return control.getInt(0) == 4 && control.getInt(4) == 0;
    }

    private static String typeOf(ByteBuffer content) {
        byte[] type = new byte[4];
        content.get(type);
        return new String(type, StandardCharsets.US_ASCII);
    }

    private record FrameControl(int width, int height, int left, int top, int delayCentiseconds) {
    }
}
