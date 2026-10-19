package io.github.ythibos.edi.adapter.home;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PersonalLayer {

    private final Path directory;

    public PersonalLayer(@Value("${edi.checkout}") Path checkout) {
        this.directory = checkout.resolve("personal");
    }

    public String anchorAddendum() {
        return ConfigurationFile.contentOf(directory.resolve("anchor.local.md")).orElse("");
    }
}
