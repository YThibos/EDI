package io.github.ythibos.edi.adapter.wallpaper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import io.github.ythibos.edi.domain.wallpaper.Eyes;

final class WallpaperArt implements RuntimeHintsRegistrar {

    private static final Map<Eyes, String> FRAMES = new EnumMap<>(Map.of(
            Eyes.OPEN, "wallpaper/face.png",
            Eyes.HALF_CLOSED, "wallpaper/eyes-half-closed.png",
            Eyes.CLOSED, "wallpaper/eyes-closed.png"));

    private final Map<Eyes, PngImage> read = new EnumMap<>(Eyes.class);

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        FRAMES.values().forEach(hints.resources()::registerPattern);
    }

    synchronized PngImage of(Eyes eyes) {
        return read.computeIfAbsent(eyes, shown -> PngImage.read(bytesOf(FRAMES.get(shown))));
    }

    private static byte[] bytesOf(String resource) {
        try (InputStream content = WallpaperArt.class.getClassLoader().getResourceAsStream(resource)) {
            if (content == null) {
                throw new IllegalStateException(resource + " is missing from the core's resources.");
            }
            return content.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
