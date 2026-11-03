package io.github.ythibos.edi.application.wallpaper;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.ythibos.edi.adapter.home.InvalidConfiguration;
import io.github.ythibos.edi.adapter.home.PersonalConfiguration;
import io.github.ythibos.edi.adapter.wallpaper.EdiWallpaper;

@Component
public class DrawTheWallpaper {

    private static final Logger LOG = LoggerFactory.getLogger(DrawTheWallpaper.class);

    private final PersonalConfiguration personal;
    private final EdiWallpaper wallpaper;
    private Optional<String> refusedLast = Optional.empty();

    public DrawTheWallpaper(PersonalConfiguration personal, EdiWallpaper wallpaper) {
        this.personal = personal;
        this.wallpaper = wallpaper;
    }

    @Scheduled(fixedDelayString = "${edi.wallpaper.round:PT1M}", initialDelayString = "${edi.wallpaper.start:PT5S}")
    public void draw() {
        try {
            wallpaper.draw(personal.blink());
            refusedLast = Optional.empty();
        } catch (InvalidConfiguration refused) {
            warnOnce(refused);
        }
    }

    private void warnOnce(InvalidConfiguration refused) {
        if (!refusedLast.equals(Optional.of(refused.getMessage()))) {
            LOG.warn("The wallpaper keeps its last drawing until the personal configuration reads again: {}", refused.getMessage());
            refusedLast = Optional.of(refused.getMessage());
        }
    }
}
