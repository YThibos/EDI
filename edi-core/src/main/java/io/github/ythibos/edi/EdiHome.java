package io.github.ythibos.edi;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class EdiHome {

    private final Path root;

    public EdiHome(@Value("${edi.home}") Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path store() {
        return root.resolve("data").resolve("edi.db");
    }

    public Path corePortFile() {
        return root.resolve("state").resolve("core.port");
    }

    public Path publishDenyList() {
        return root.resolve("config").resolve("publish-denylist");
    }
}
