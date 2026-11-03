package io.github.ythibos.edi.adapter.wallpaper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.domain.wallpaper.Blink;

@Component
@ImportRuntimeHints(WallpaperArt.class)
public class EdiWallpaper {

    private final Path file;
    private final WallpaperArt art = new WallpaperArt();
    private Optional<Blink> drawn = Optional.empty();

    public EdiWallpaper(EdiHome home) {
        this.file = home.wallpaper();
    }

    public synchronized boolean draw(Blink blink) {
        if (drawn.equals(Optional.of(blink)) && Files.exists(file)) {
            return false;
        }
        replace(AnimatedPng.looping(blink.loop().stream().map(glimpse -> new AnimatedPng.Still(art.of(glimpse.eyes()), glimpse.shown())).toList()));
        drawn = Optional.of(blink);
        return true;
    }

    private void replace(byte[] animation) {
        try {
            Files.createDirectories(file.getParent());
            Path written = Files.createTempFile(file.getParent(), file.getFileName() + "-", ".tmp");
            Files.write(written, animation);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
