package io.github.ythibos.edi.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExampleHome {

    private ExampleHome() {
    }

    public static String file(String relative) throws IOException {
        Path root = Path.of(System.getProperty("edi.repository.root", "..")).toAbsolutePath().normalize();
        return Files.readString(root.resolve("examples/edi-home").resolve(relative));
    }
}
