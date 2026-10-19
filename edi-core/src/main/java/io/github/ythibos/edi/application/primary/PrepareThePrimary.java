package io.github.ythibos.edi.application.primary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.stereotype.Service;

import io.github.ythibos.edi.EdiHome;
import io.github.ythibos.edi.adapter.home.MemoryFiles;
import io.github.ythibos.edi.adapter.home.PersonalLayer;
import io.github.ythibos.edi.application.port.Clock;
import io.github.ythibos.edi.domain.primary.PrimaryPrompt;

@Service
@ImportRuntimeHints(PrimaryLayer.class)
public class PrepareThePrimary {

    private static final DateTimeFormatter SESSION_STAMP = DateTimeFormatter.ofPattern("yyMMdd-HHmm");

    private final EdiHome home;
    private final MemoryFiles memory;
    private final PersonalLayer personal;
    private final Clock clock;

    public PrepareThePrimary(EdiHome home, MemoryFiles memory, PersonalLayer personal, Clock clock) {
        this.home = home;
        this.memory = memory;
        this.personal = personal;
        this.clock = clock;
    }

    public PrimaryLaunch prepare() {
        String prompt = PrimaryPrompt.composed(PrimaryLayer.read(PrimaryLayer.ANCHOR), personal.anchorAddendum(), memory.loadedAtSessionStart());
        Path settings = written(home.primaryState().resolve("settings.json"), PrimaryLayer.read(PrimaryLayer.SETTINGS));
        Path systemPrompt = written(home.primaryState().resolve("system-prompt.md"), prompt);
        return new PrimaryLaunch(home.root(), settings, systemPrompt, "edi-" + SESSION_STAMP.format(clock.now().atZone(ZoneId.systemDefault())));
    }

    private static Path written(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            return Files.writeString(file, content);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
